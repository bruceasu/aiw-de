package me.asu.aidev.development.application;

/**
 * Compatibility tombstone for the pre-R6-S3 handler name.
 *
 * <p>R6-S3 superseded this Spring component with {@link CodeChangeCompletedHandler}, which handles
 * both IMPLEMENTATION and REPAIR results. This source file intentionally remains at the historical
 * path so archive-overwrite upgrades replace an older Spring-managed source instead of leaving two
 * active completion handlers in the source tree.</p>
 *
 * <p>Do not add a Spring stereotype annotation to this class.</p>
 */
@Deprecated(forRemoval = true)
final class ImplementationCompletedHandler {
    private ImplementationCompletedHandler() {}
}
