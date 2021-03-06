package se.fortnox.reactivewizard.jaxrs;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpResponseStatus;
import rx.Observable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

public class ByteBufCollector {

    private final int maxReqSize;

    public ByteBufCollector() {
        this.maxReqSize = 10 * 1024 * 1024;
    }

    public ByteBufCollector(int maxReqSize) {
        this.maxReqSize = maxReqSize;
    }

    public Observable<String> collectString(Observable<ByteBuf> input) {
        return input
            .collect(ByteArrayOutputStream::new, this::collectChunks)
            .map(this::decodeBody);
    }

    private String decodeBody(ByteArrayOutputStream buf) {
        try {
            return buf.toString(Charset.defaultCharset().name());
        } catch (UnsupportedEncodingException e) {
            throw new WebException(HttpResponseStatus.BAD_REQUEST, "unsupported.encoding");
        }
    }

    private void collectChunks(ByteArrayOutputStream buf, ByteBuf bytes) {
        try {
            int length = bytes.readableBytes();
            if (buf.size() + length > maxReqSize) {
                throw new WebException(HttpResponseStatus.BAD_REQUEST, "too.large.input");
            } else {
                bytes.readBytes(buf, length);
            }
        } catch (IOException e) {
            throw new WebException(HttpResponseStatus.BAD_REQUEST, e);
        } finally {
            bytes.release();
        }
    }

    public Observable<byte[]> collectBytes(Observable<ByteBuf> content) {
        return content
            .collect(ByteArrayOutputStream::new, this::collectChunks)
            .map(ByteArrayOutputStream::toByteArray);
    }
}
