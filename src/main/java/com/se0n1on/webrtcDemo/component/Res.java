package com.se0n1on.webrtcDemo.component;

import lombok.Getter;

@Getter
public class Res<T> {

    private Boolean success;
    private T data;

    private Res(Boolean success, T data){
        this.success = success;
        this.data = data;
    }

    public static <T> ResBuilder<T>  builder(){
        return new ResBuilder<T>();
    }

    public static class ResBuilder<T> {
        private Boolean success;
        private T data;

        private ResBuilder() {}

        public ResBuilder success(Boolean success) {
            this.success = success;
            return this;
        }

        public ResBuilder data(T data) {
            this.data = data;
            return this;
        }

        public Res build(){
            return new Res(success, data);
        }
    }
}