package com.zp.imageprocessor.lambda;

import com.amazonaws.mobileconnectors.lambdainvoker.LambdaFunction;
import java.util.Map;

public interface ILambdaInvoker {
    @LambdaFunction(functionName = "image-processor")
    String ping(Map event);
    @LambdaFunction(functionName = "image-processor")
    String convert(ImageConvertRequest request);
}