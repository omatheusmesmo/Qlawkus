package dev.omatheusmesmo.qlawkus.specialists;

public final class SpecialistProblem extends RuntimeException {

    private final int status;

    SpecialistProblem(int status, String message) {
        super(message);
        this.status = status;
    }

    int status() {
        return status;
    }

    static SpecialistProblem invalid(String message) {
        return new SpecialistProblem(400, message);
    }

    static SpecialistProblem unavailable() {
        return new SpecialistProblem(503, "The specialist catalog is unavailable.");
    }
}
