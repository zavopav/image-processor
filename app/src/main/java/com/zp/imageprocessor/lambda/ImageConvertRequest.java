package com.zp.imageprocessor.lambda;

import java.util.List;
public class ImageConvertRequest extends LambdaEvent {
    private String base64Image;
    private String inputExtension;
    private String outputExtension;
    private List customArgs;
    public ImageConvertRequest() {super("convert");}
    public String getBase64Image() {return base64Image;}
    public void setBase64Image(String base64Image) {this.base64Image = base64Image;}
    public String getInputExtension() {return inputExtension;}
    public void setInputExtension(String inputExtension) {this.inputExtension = inputExtension;}
    public String getOutputExtension() {return outputExtension;}
    public void setOutputExtension(String outputExtension) {this.outputExtension = outputExtension;}
    public List getCustomArgs() {return customArgs;}
    public void setCustomArgs(List customArgs) {this.customArgs = customArgs;}
}