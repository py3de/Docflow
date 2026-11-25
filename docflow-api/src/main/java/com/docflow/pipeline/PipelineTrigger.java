package com.docflow.pipeline;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Starts the pipeline in the background once the upload's transaction has committed,
 * so the upload request returns immediately and the pipeline never sees uncommitted data.
 * Step 3 replaces this with a message queue (survives restarts, can run on other machines).
 */
@Component
public class PipelineTrigger {

    private final DocumentPipeline pipeline;

    public PipelineTrigger(DocumentPipeline pipeline) {
        this.pipeline = pipeline;
    }

    @Async("pipelineExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReceived(DocumentReceivedEvent event) {
        pipeline.process(event.documentId());
    }
}
