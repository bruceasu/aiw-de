package me.asu.aidev.development.port;

import me.asu.aidev.development.domain.EngineeringGate;

/**
 * Application boundary invoked after one immutable engineering gate has been finalized.
 * Listeners may materialize downstream authority but must not mutate the gate itself.
 */
public interface EngineeringGateFinalizedListener {
    void onFinalized(EngineeringGate gate);
}
