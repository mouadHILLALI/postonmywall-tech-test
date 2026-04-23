package com.postonmywall.file;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public class InitiateUploadRequest {

    @NotBlank
    private String filename;

    @NotBlank
    private String contentType;

    @Positive
    private long sizeBytes;

    public String getFilename()    { return filename; }
    public String getContentType() { return contentType; }
    public long   getSizeBytes()   { return sizeBytes; }

    public void setFilename(String filename)       { this.filename = filename; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public void setSizeBytes(long sizeBytes)       { this.sizeBytes = sizeBytes; }
}
