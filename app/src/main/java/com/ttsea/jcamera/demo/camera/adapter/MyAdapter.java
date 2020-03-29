package com.ttsea.jcamera.demo.camera.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.ttsea.jcamera.demo.camera.model.ItemEntity;

import java.util.List;

public class MyAdapter extends BaseAdapter {
    private List<ItemEntity> mList;
    private LayoutInflater mInflater;

    public MyAdapter(Context context, List<ItemEntity> list) {
        this.mList = list;
        this.mInflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        return mList == null ? 0 : mList.size();
    }

    @Override
    public Object getItem(int position) {
        return mList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null || !(convertView.getTag() instanceof ViewHolder)) {
            convertView = mInflater.inflate(android.R.layout.simple_list_item_1, parent, false);

            holder = new ViewHolder();
            holder.text1 = convertView.findViewById(android.R.id.text1);

            convertView.setTag(holder);

        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        ItemEntity item = mList.get(position);
        if (item.isSelected()) {
            holder.text1.setText(item.getKey() + " *");
        } else {
            holder.text1.setText(item.getKey());
        }
        return convertView;
    }


    private static class ViewHolder {
        private TextView text1;
    }
}