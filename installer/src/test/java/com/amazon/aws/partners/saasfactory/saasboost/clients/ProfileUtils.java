package com.amazon.aws.partners.saasfactory.saasboost.clients;

import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.core.SdkSystemSetting;
import software.amazon.awssdk.profiles.Profile;

import java.io.*;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ProfileUtils {
    public static void updateOrCreateProfile(String filename,
                                             String profile,
                                             AwsCredentials newCredentials)
            throws IOException {
        // this will automatically create the file if it does not already exist
        try (FileWriter updatingOutputStream = new FileWriter(filename, false)) {
            writeProfileToFileWriter(
                    Profile.builder()
                            .name(profile)
                            .properties(propertiesFromCredentials(newCredentials))
                            .build(),
                    updatingOutputStream);
        }
    }

    private static void writeProfileToFileWriter(Profile profile, FileWriter fileWriter) throws IOException {
        fileWriter.write("[" + profile.name() + "]\n");
        for (Map.Entry<String, String> property : profile.properties().entrySet()) {
            fileWriter.write(property.getKey() + " = " + property.getValue() + "\n");
        }
        fileWriter.write("\n");
    }

    private static Map<String, String> propertiesFromCredentials(AwsCredentials awsCredentials) {
        Map<String, String> properties = new HashMap<>();
        properties.put(
                SdkSystemSetting.AWS_ACCESS_KEY_ID.environmentVariable().toLowerCase(),
                awsCredentials.accessKeyId());
        properties.put(
                SdkSystemSetting.AWS_SECRET_ACCESS_KEY.environmentVariable().toLowerCase(),
                awsCredentials.secretAccessKey());
        if (awsCredentials instanceof AwsSessionCredentials) {
            properties.put(
                    SdkSystemSetting.AWS_SESSION_TOKEN.environmentVariable().toLowerCase(),
                    ((AwsSessionCredentials) awsCredentials).sessionToken());
        }
        return properties;
    }
}
