package com.postonmywall.file;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public class ConfirmUploadRequest {

    @NotBlank
    private String s3Key;

    @NotBlank
    private String originalName;

    @NotBlank
    private String contentType;

    @Positive
    private long sizeBytes;

    public String getS3Key()        { return s3Key; }
    public String getOriginalName() { return originalName; }
    public String getContentType()  { return contentType; }
    public long   getSizeBytes()    { return sizeBytes; }

    public void setS3Key(String s3Key)             { this.s3Key = s3Key; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public void setSizeBytes(long sizeBytes)       { this.sizeBytes = sizeBytes; }
}
