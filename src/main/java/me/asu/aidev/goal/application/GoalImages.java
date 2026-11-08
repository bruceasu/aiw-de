package me.asu.aidev.goal.application;

import java.util.*;
import me.asu.aidev.goal.domain.GoalData.Draft;

/** Only explicitly selected image bytes enter the immutable discussion request. */
public final class GoalImages {
    public static final String CAPABILITY="model-images-v1";
    public static final int MAX_BYTES=512*1024;
    public record Image(String id,String mime,String sha256,String base64) {}
    private GoalImages() {}
    public static String mime(byte[] bytes) {
        if(bytes.length>=24&&bytes[0]==(byte)137&&bytes[1]==80&&bytes[2]==78&&bytes[3]==71&&bytes[4]==13&&bytes[5]==10&&bytes[6]==26&&bytes[7]==10)return "image/png";
        if(bytes.length>=4&&bytes[0]==(byte)255&&bytes[1]==(byte)216&&bytes[2]==(byte)255)return "image/jpeg";
        if(bytes.length>=10&&bytes[0]==71&&bytes[1]==73&&bytes[2]==70&&bytes[3]==56&&(bytes[4]==55||bytes[4]==57)&&bytes[5]==97)return "image/gif";
        if(bytes.length>=16&&bytes[0]==82&&bytes[1]==73&&bytes[2]==70&&bytes[3]==70&&bytes[8]==87&&bytes[9]==69&&bytes[10]==66&&bytes[11]==80)return "image/webp";
        return null;
    }
    public static List<Image> selected(GoalAttachments files,Draft draft,List<String> selected) {
        if(selected.isEmpty())return List.of();
        var available=files.list(draft.id(),draft.actor());
        var result=new ArrayList<Image>();int total=0;
        for(String id:selected) {
            var attachment=available.stream().filter(a->a.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Attachment is not in this goal"));
            if(!attachment.status().equals("IMAGE_READY"))continue;
            byte[] bytes=files.bytes(draft.id(),id,draft.actor());String mime=mime(bytes);
            if(mime==null||!GoalAttachments.hash(bytes).equals(attachment.sha256()))throw new IllegalStateException("Selected image changed or has an unsupported format");
            total+=bytes.length;
            if(result.size()>=4||total>MAX_BYTES)throw new IllegalArgumentException("每条讨论最多发送 4 张图片，原始图片合计不超过 512 KiB；请先缩小图片。");
            result.add(new Image(id,mime,attachment.sha256(),Base64.getEncoder().encodeToString(bytes)));
        }
        return List.copyOf(result);
    }
}
