package ch.bissbert.command.creator;

public class Reference<T> {
    private T reference;

    public Reference() {
    }

    public T getReference() {
        return reference;
    }

    public void setReference(T reference) {
        this.reference = reference;
    }
}
