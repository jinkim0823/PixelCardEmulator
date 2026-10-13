package io.github.realtvop.pixelcardemulator;

import android.content.Context;

import androidx.annotation.Keep;

import java.io.IOException;

/** Runs only inside a Shizuku user-service process with shell or root identity. */
public final class PrivilegeUserService extends IPrivilegeService.Stub {
    public PrivilegeUserService() {
    }

    @Keep
    public PrivilegeUserService(Context context) {
    }

    @Override
    public int grantSecureSettings(String packageName) {
        if (!packageName.matches("[A-Za-z0-9_.]+")) {
            throw new IllegalArgumentException("Invalid package name");
        }
        try {
            Process process = new ProcessBuilder(
                    "/system/bin/pm",
                    "grant",
                    packageName,
                    "android.permission.WRITE_SECURE_SETTINGS"
            ).redirectErrorStream(true).start();
            try (var output = process.getInputStream()) {
                // Drain output so pm cannot block if it reports an error.
                while (output.read() != -1) {
                    // Nothing to retain: the exit status is returned to the app process.
                }
            }
            return process.waitFor();
        } catch (IOException error) {
            return -1;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }

    @Override
    public void destroy() {
        System.exit(0);
    }
}
