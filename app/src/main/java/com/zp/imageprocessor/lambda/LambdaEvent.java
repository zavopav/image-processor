package com.zp.imageprocessor.lambda;

public class LambdaEvent {
    private String operation;
    public String getOperation() {return operation;}
    public void setOperation(String operation) {this.operation = operation;}
    public LambdaEvent(String operation) {setOperation(operation);}
}