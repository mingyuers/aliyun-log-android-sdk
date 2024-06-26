package com.aliyun.sls.android.producer.example.example.trace.ui.core.list;

import java.util.ArrayList;
import java.util.List;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.aliyun.sls.android.producer.example.example.trace.core.TraceViewModel;

/**
 * @author gordon
 * @date 2021/10/17
 */
public abstract class BaseListViewModel<ITEM> extends TraceViewModel {

    protected MutableLiveData<List<ITEM>> items;


    public BaseListViewModel(String modelName) {
        super(modelName);
        items = new MutableLiveData<>(new ArrayList<>());
    }

    public LiveData<List<ITEM>> getItems() {
        return items;
    }

    public void requestItemsFromServer() {
        //tracer.spanBuilder(String.format("request_%s_items", modelName)).startSpan().end();

        fetchItemsFromServer();
    }

    protected abstract void fetchItemsFromServer();

    public static class Status {
        public static final int TYPE_DEF_SUCCESS = 1;
        public int type = TYPE_DEF_SUCCESS;
        public boolean success;
        public String error;
        public String code;

        public Status() {
            this.success = true;
        }

        public Status(boolean success, String code, String error) {
            this(1, success, code, error);
        }

        public Status(int type, boolean success, String code, String error) {
            this.type = type;
            this.success = success;
            this.code = code;
            this.error = error;
        }

        public static Status success() {
            return new Status();
        }

        public static Status success(int type) {
            return new Status(type, true, null, null);
        }

        public static Status error(int type, String code, String error) {
            return new Status(type, false, code, error);
        }

        public static Status error(String code, String error) {
            return error(1, code, error);
        }

    }

}
