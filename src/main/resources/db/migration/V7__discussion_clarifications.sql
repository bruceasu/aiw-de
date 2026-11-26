CREATE TABLE discussion_clarification (
    thread_id VARCHAR(36) NOT NULL,
    question_id VARCHAR(100) NOT NULL,
    answer_id VARCHAR(100),
    secretary_id VARCHAR(128),
    delegation_id VARCHAR(36),
    PRIMARY KEY (thread_id, question_id),
    FOREIGN KEY (thread_id, question_id) REFERENCES discussion_post(thread_id, id),
    FOREIGN KEY (thread_id, answer_id) REFERENCES discussion_post(thread_id, id)
);
CREATE INDEX discussion_clarification_secretary ON discussion_clarification(secretary_id, delegation_id);

INSERT INTO discussion_clarification(thread_id, question_id)
 SELECT thread_id, id FROM discussion_post WHERE author_role='MANAGER';
UPDATE discussion_thread SET secretary_id=NULL WHERE secretary_id IS NOT NULL;
