package com.yliahs.common.handler.base;

public class StatefulHandler<S> {
    private S state;

    public StatefulHandler(S state) {
        this.state = state;
    }

    public S getState() {
        return state;
    }

    public void setState(S state) {
        this.state = state;
    }
}
