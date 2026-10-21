package net.fabricmc.resourcetracker.model;

import java.util.List;

public final class CompletionRegression {
    public static void main(String[] args) {
        var tracker = new CompletionTracker();
        var a = new CompletionTracker.Goal("stone", false);
        var b = new CompletionTracker.Goal("dirt", false);
        var doneA = new CompletionTracker.Goal("stone", true);
        var doneB = new CompletionTracker.Goal("dirt", true);
        check(tracker.observe("items", List.of(a, b)).resources().isEmpty(), "initial state is quiet");
        var changes = tracker.observe("items", List.of(doneA, b));
        check(changes.resources().equals(List.of("stone")) && !changes.listCompleted(), "one resource completion");
        check(tracker.observe("items", List.of(doneA, b)).resources().isEmpty(), "stable resource does not repeat");
        changes = tracker.observe("items", List.of(doneA, doneB));
        check(changes.listCompleted() && changes.resources().equals(List.of("dirt")), "list completion edge");
        changes = tracker.observe("items", List.of(doneA, doneB));
        check(!changes.listCompleted() && changes.resources().isEmpty(), "stable completed list is quiet");
        changes = tracker.observe("raw", List.of(doneA));
        check(!changes.listCompleted() && changes.resources().isEmpty(), "separate dataset starts quietly");
        tracker.observe("raw", List.of(a));
        check(tracker.observe("raw", List.of(doneA)).listCompleted(), "raw completion is independent");
        check(!tracker.observe("items", List.of()).listCompleted(), "clearing a list is not completion");
        tracker.forget("raw");
        changes = tracker.observe("raw", List.of(doneA));
        check(!changes.listCompleted() && changes.resources().isEmpty(), "loading a completed dataset stays quiet");
        System.out.println("Completion regression checks passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
