package com.postonmywall.file;

public class InitiateUploadResponse {

    private final String uploadUrl;
    private final String s3Key;

    public InitiateUploadResponse(String uploadUrl, String s3Key) {
        this.uploadUrl = uploadUrl;
        this.s3Key     = s3Key;
    }

    public String getUploadUrl() { return uploadUrl; }
    public String getS3Key()     { return s3Key; }
}
