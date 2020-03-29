package com.ttsea.jcamera.annotation;

import com.ttsea.jcamera.core.Constants;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import androidx.annotation.IntDef;

@Retention(RetentionPolicy.CLASS)
@Target({ElementType.PARAMETER, ElementType.FIELD, ElementType.METHOD})
@IntDef({Constants.FACING_BACK, Constants.FACING_FRONT})
public @interface Facing {
}
