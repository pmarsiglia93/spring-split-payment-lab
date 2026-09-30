package com.example.transferservice.exception;

public class TransferSimulationException extends RuntimeException {
    public TransferSimulationException() {
        super("Simulated failure after the transfer was persisted");
    }
}
