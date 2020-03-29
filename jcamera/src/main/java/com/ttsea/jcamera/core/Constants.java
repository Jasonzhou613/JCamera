package com.ttsea.jcamera.core;

public interface Constants {

    /** 后置摄像头标识 */
    int FACING_BACK = 0;
    /** 前置摄像头标识 */
    int FACING_FRONT = 1;

    int FLASH_OFF = 0;
    int FLASH_ON = 1;
    int FLASH_AUTO = 2;
    /** 版本21之前支持 */
    int FLASH_TORCH = 3;
    int FLASH_RED_EYE = 4;
    /** 版本21之后支持 */
    int FLASH_ON_ALWAYS = 5;
    /** 版本28之后支持，暂时忽略 */
    int FLASH_ON_EXTERNAL = 6;
}
