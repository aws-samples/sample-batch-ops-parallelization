package com.amazon.bopspar.persistence.utils;

import com.amazon.bopspar.model.InvalidInputException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class ExceptionUtils {
    private static final Logger LOG = LogManager.getLogger(ExceptionUtils.class);

    private ExceptionUtils() {
    }

    public static void checkArgument(boolean var0) {
        if (!var0) {
            throw new InvalidInputException();
        }
    }

    public static void checkArgument(boolean var0, Object var1) {
        if (!var0) {
            throw new InvalidInputException(String.valueOf(var1));
        }
    }
}
