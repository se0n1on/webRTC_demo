package com.huvet.tg.domain;

import java.util.Objects;

public class WebSocketMessage {
    private String from;
    private String type;
    private String data;
    private Object candidate;
    private Object sdp;
    private Object stream;

    public WebSocketMessage() {
    }

    public WebSocketMessage(final String from,
                            final String type,
                            final String data,
                            final Object candidate,
                            final Object sdp,
                            final Object stream) {
        this.from = from;
        this.type = type;
        this.data = data;
        this.candidate = candidate;
        this.sdp = sdp;
        this.stream = stream;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(final String from) {
        this.from = from;
    }

    public String getType() {
        return type;
    }

    public void setType(final String type) {
        this.type = type;
    }

    public String getData() {
        return data;
    }

    public void setData(final String data) {
        this.data = data;
    }

    public Object getCandidate() {
        return candidate;
    }

    public void setCandidate(final Object candidate) {
        this.candidate = candidate;
    }

    public Object getSdp() {
        return sdp;
    }

    public void setSdp(final Object sdp) {
        this.sdp = sdp;
    }

    public Object getStream() {
        return stream;
    }

    public void setStream(final Object stream) {
        this.stream = stream;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final WebSocketMessage message = (WebSocketMessage) o;
        return Objects.equals(getFrom(), message.getFrom()) &&
                Objects.equals(getType(), message.getType()) &&
                Objects.equals(getData(), message.getData()) &&
                Objects.equals(getCandidate(), message.getCandidate()) &&
                Objects.equals(getSdp(), message.getSdp()) &&
                Objects.equals(getStream(), message.getStream());
    }

    @Override
    public int hashCode() {

        return Objects.hash(getFrom(), getType(), getData(), getCandidate(), getSdp(), getStream());
    }

    @Override
    public String toString() {
        return "WebSocketMessage{" +
                "from='" + from + '\'' +
                ", type='" + type + '\'' +
                ", data='" + data + '\'' +
                ", candidate=" + candidate +
                ", sdp=" + sdp +
                ", stream=" + stream +
                '}';
    }
}
