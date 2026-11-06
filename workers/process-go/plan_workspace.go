package main

import (
    "context"
    "crypto/sha256"
    "encoding/hex"
    "encoding/json"
    "errors"
    "io/fs"
    "os"
    "path/filepath"
    "sort"
    "strings"
    "sync"
    "time"
)

type HandoffReference struct { Manifest string `json:"manifest"`; SHA256 string `json:"sha256"` }
type HandoffInput struct { Location string `json:"location"`; Manifest string `json:"manifest"`; SHA256 string `json:"sha256"` }
type PlanManifest struct {
    Protocol string `json:"protocol"`; TaskID string `json:"taskId"`; AttemptID string `json:"attemptId"`
    PlanID string `json:"planId"`; WorkingDirectory string `json:"workingDirectory"`; CompletedAt int64 `json:"completedAt"`
    Report string `json:"report"`; ReportSHA256 string `json:"reportSha256"`; Verdict string `json:"verdict"`; GitResult *WorkspaceResult `json:"gitResult"`
}
func planKey(value string) string { sum:=sha256.Sum256([]byte(value));return hex.EncodeToString(sum[:]) }
func planDirectory(root string, parts ...string) (string,error) {
    current:=root
    for _,part:=range parts {
        current=filepath.Join(current,part)
        if err:=os.Mkdir(current,0755);err!=nil&&!os.IsExist(err){return "",err}
        info,err:=os.Lstat(current);if err!=nil{return "",err}
        if !info.IsDir()||info.Mode()&os.ModeSymlink!=0{return "",errors.New("HANDOFF_DIRECTORY_UNSAFE")}
        actual,err:=filepath.EvalSymlinks(current);if err!=nil{return "",err}
        parent,err:=filepath.EvalSymlinks(filepath.Dir(current));if err!=nil||filepath.Dir(actual)!=parent{return "",errors.New("HANDOFF_DIRECTORY_UNSAFE")}
    }
    return current,nil
}
func planLock(ctx context.Context,root string)(func(),error){
    parent,err:=planDirectory(root,".ai","locks");if err!=nil{return nil,err};target:=filepath.Join(parent,"execution.lock")
    ticker:=time.NewTicker(50*time.Millisecond);defer ticker.Stop()
    for {
        if err=ctx.Err();err!=nil{return nil,err}
        err=os.Mkdir(target,0755)
        if err==nil {var once sync.Once;return func(){once.Do(func(){_ = os.Remove(target)})},nil}
        if !os.IsExist(err){return nil,err}
        select{case <-ctx.Done():return nil,ctx.Err();case <-ticker.C:}
    }
}
func planInputs(task ClaimedTask)([]PlanManifest,error){
    result:=[]PlanManifest{}
    for _,input:=range task.Inputs{
        ref:=input.Handoff;if ref==nil{continue}
        root,err:=filepath.EvalSymlinks(ref.Location);if err!=nil{return nil,err}
        target:=filepath.Join(root,filepath.FromSlash(ref.Manifest));if !childPath(root,target){return nil,errors.New("HANDOFF_PATH_INVALID")}
        for cursor:=target;cursor!=root;cursor=filepath.Dir(cursor){
            info,e:=os.Lstat(cursor);if e!=nil{return nil,e};if info.Mode()&os.ModeSymlink!=0{return nil,errors.New("HANDOFF_PATH_UNSAFE")}
        }
        info,err:=os.Stat(target);if err!=nil{return nil,err};if info.Size()>1024*1024{return nil,errors.New("HANDOFF_CAPACITY_EXCEEDED")}
        raw,err:=os.ReadFile(target);if err!=nil{return nil,err};sum:=sha256.Sum256(raw)
        if hex.EncodeToString(sum[:])!=ref.SHA256{return nil,errors.New("HANDOFF_HASH_MISMATCH")}
        var manifest PlanManifest;if err=json.Unmarshal(raw,&manifest);err!=nil{return nil,err}
        if manifest.Protocol!="aiw.handoff.v1"||manifest.TaskID!=input.TaskID||manifest.AttemptID!=input.AttemptID{return nil,errors.New("HANDOFF_SOURCE_MISMATCH")}
        reportPath:=filepath.Join(filepath.Dir(target),"report.md");reportInfo,e:=os.Lstat(reportPath);if e!=nil{return nil,e}
        if manifest.Report!="report.md"||!reportInfo.Mode().IsRegular()||reportInfo.Size()>1024*1024{return nil,errors.New("HANDOFF_REPORT_INVALID")}
        report,e:=os.ReadFile(reportPath);if e!=nil{return nil,e};reportSum:=sha256.Sum256(report)
        if hex.EncodeToString(reportSum[:])!=manifest.ReportSHA256{return nil,errors.New("HANDOFF_REPORT_HASH_MISMATCH")}
        candidate,err:=filepath.EvalSymlinks(manifest.WorkingDirectory);if err!=nil{return nil,err}
        if candidate!=root&&!childPath(root,candidate){return nil,errors.New("HANDOFF_WORKING_DIRECTORY_UNSAFE")}
        result=append(result,manifest)
    }
    sort.SliceStable(result,func(i,j int)bool{return result[i].CompletedAt<result[j].CompletedAt})
    return result,nil
}
func preparePlanWorkspace(ctx context.Context,cfg Config,task ClaimedTask,evidence *AttemptEvidence)(*GitWorkspace,error){
    if task.Location==""{return nil,errors.New("PLAN_LOCATION_REQUIRED")}
    if err:=os.MkdirAll(task.Location,0755);err!=nil{return nil,err}
    root,err:=filepath.EvalSymlinks(task.Location);if err!=nil{return nil,err};root,err=filepath.Abs(root);if err!=nil{return nil,err}
    inputs,err:=planInputs(task);if err!=nil{return nil,err}
    source:=root
    if len(inputs)>0{candidate:=inputs[len(inputs)-1].WorkingDirectory;if candidate==root||childPath(root,candidate){source=candidate}}
    release,err:=planLock(ctx,root);if err!=nil{return nil,err}
    workspace:=&GitWorkspace{cfg:cfg,cwd:source,ctx:ctx,recorder:evidence,planRoot:root,planRelease:release,handoffInputs:inputs}
    if cfg.Profile.PrimarySkill=="goal-discussion" || task.SubjectType=="goal-discussion" || task.SubjectType=="workflow-recommendation" {
        cwd,e:=planDirectory(root,".ai","sandboxes",planKey(task.AttemptID));if e!=nil{release();return nil,e}
        ownRelease,e:=planLock(ctx,cwd);if e!=nil{release();return nil,e};release()
        workspace.cwd=cwd;workspace.planRelease=ownRelease
        return workspace,nil
    }
    repos:=[]string{};count:=0
    err=filepath.WalkDir(root,func(file string,entry fs.DirEntry,e error)error{
        if e!=nil{return e};if ctx.Err()!=nil{return ctx.Err()};if !entry.IsDir(){return nil}
        if file!=root {switch entry.Name(){case ".git",".ai",".aiw","node_modules",".venv":return filepath.SkipDir}}
        count++;if count>50000{return errors.New("REPOSITORY_LAYOUT_LIMIT")}
        if _,e:=os.Stat(filepath.Join(file,".git"));e==nil{repos=append(repos,file)}
        return nil
    })
    if err!=nil{release();return nil,err}
    if len(repos)==1&&repos[0]==root{
        base,e:=gitTextContext(ctx,source,"rev-parse","--verify","HEAD^{commit}")
        if e==nil{
            dirty,e:=gitTextContext(ctx,source,"status","--porcelain","--",".",":(exclude).ai",":(exclude).ai/**")
            if e!=nil&&e.Error()=="PROCESS_TREE_UNCERTAIN"{return nil,e}
            if e==nil&&strings.TrimSpace(dirty)==""{
                parent,e:=planDirectory(root,".ai","worktrees",planKey(task.PlanID));if e!=nil{release();return nil,e}
                target:=filepath.Join(parent,planKey(task.AttemptID))
                if _,e=gitTextContext(ctx,root,"worktree","add","--detach",target,strings.TrimSpace(base));e==nil{
                    ownRelease,e:=planLock(ctx,target);if e!=nil{release();return nil,e};release()
                    workspace.cwd=target;workspace.repositoryRoot=root;workspace.planRelease=ownRelease
                    workspace.assignment=&WorkspaceAssignment{BaseRevision:strings.TrimSpace(base)};workspace.owned=true
                }else if e.Error()=="PROCESS_TREE_UNCERTAIN"{return nil,e}
            }
        }else if e.Error()=="PROCESS_TREE_UNCERTAIN"{return nil,e}
    }
    if ctx.Err()!=nil{release();return nil,ctx.Err()}
    return workspace,nil
}
func (w *GitWorkspace) handoff(task ClaimedTask,report *CompletionReport,result *WorkspaceResult)(*HandoffReference,error){
    if report==nil{return nil,errors.New("COMPLETION_REPORT_REQUIRED")}
    target,err:=planDirectory(w.planRoot,".ai","handoffs",planKey(task.TaskID),planKey(task.AttemptID));if err!=nil{return nil,err}
    if err=writePlanFile(filepath.Join(target,"report.md"),[]byte(report.Summary));err!=nil{return nil,err}
    manifest:=PlanManifest{Protocol:"aiw.handoff.v1",TaskID:task.TaskID,AttemptID:task.AttemptID,PlanID:task.PlanID,
        WorkingDirectory:w.cwd,CompletedAt:time.Now().UnixMilli(),Report:"report.md",ReportSHA256:planKey(report.Summary),Verdict:report.Verdict,GitResult:result}
    raw,err:=json.MarshalIndent(manifest,"","  ");if err!=nil{return nil,err}
    file,err:=os.OpenFile(filepath.Join(target,"manifest.json"),os.O_CREATE|os.O_EXCL|os.O_WRONLY,0644);if err!=nil{return nil,err}
    _,err=file.Write(raw);closeErr:=file.Close();if err!=nil{return nil,err};if closeErr!=nil{return nil,closeErr}
    relative,err:=filepath.Rel(w.planRoot,filepath.Join(target,"manifest.json"));if err!=nil{return nil,err};sum:=sha256.Sum256(raw)
    return &HandoffReference{Manifest:filepath.ToSlash(relative),SHA256:hex.EncodeToString(sum[:])},nil
}
func writePlanFile(target string,raw []byte)error{
    file,err:=os.OpenFile(target,os.O_CREATE|os.O_EXCL|os.O_WRONLY,0644);if err!=nil{return err}
    _,err=file.Write(raw);closed:=file.Close();if err!=nil{return err};return closed
}
