package com.myorg;

import software.amazon.awscdk.App;

public final class CdkApp {
    public static void main(final String[] args) {
        App app = new App();

        new CdkStack(app, "CdkStack");

        app.synth();
    }
}
