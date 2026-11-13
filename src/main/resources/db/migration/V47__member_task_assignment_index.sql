-- Member workload aggregation follows the frozen run/node assignment.
CREATE INDEX worker_team_task_assignment ON worker_team_task(run_id,node_key);
