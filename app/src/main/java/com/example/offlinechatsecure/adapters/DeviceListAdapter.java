package com.example.offlinechatsecure.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.offlinechatsecure.R;
import com.example.offlinechatsecure.models.BluetoothDeviceItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DeviceListAdapter extends RecyclerView.Adapter<DeviceListAdapter.DeviceViewHolder> {

    public interface OnDeviceClickListener {
        void onDeviceClick(@NonNull BluetoothDeviceItem item);
    }

    private final List<BluetoothDeviceItem> source = new ArrayList<>();
    private final List<BluetoothDeviceItem> visible = new ArrayList<>();
    private final OnDeviceClickListener onDeviceClickListener;
    private String filterQuery = "";

    public DeviceListAdapter(@NonNull OnDeviceClickListener onDeviceClickListener) {
        this.onDeviceClickListener = onDeviceClickListener;
    }

    public void setDevices(@NonNull List<BluetoothDeviceItem> updatedDevices) {
        source.clear();
        source.addAll(updatedDevices);
        applyFilter();
    }

    public void setFilter(@NonNull String query) {
        filterQuery = query.trim().toLowerCase(Locale.US);
        applyFilter();
    }

    private void applyFilter() {
        visible.clear();
        if (filterQuery.isEmpty()) {
            visible.addAll(source);
        } else {
            for (BluetoothDeviceItem item : source) {
                if (item.getName().toLowerCase(Locale.US).contains(filterQuery)
                        || item.getAddress().toLowerCase(Locale.US).contains(filterQuery)) {
                    visible.add(item);
                }
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_device, parent, false);
        return new DeviceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {
        BluetoothDeviceItem item = visible.get(position);
        holder.bind(item, onDeviceClickListener);
    }

    @Override
    public int getItemCount() {
        return visible.size();
    }

    static class DeviceViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvName;
        private final TextView tvAddress;
        private final View bar1;
        private final View bar2;
        private final View bar3;
        private final View bar4;

        DeviceViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvDeviceName);
            tvAddress = itemView.findViewById(R.id.tvDeviceAddress);
            bar1 = itemView.findViewById(R.id.signalBar1);
            bar2 = itemView.findViewById(R.id.signalBar2);
            bar3 = itemView.findViewById(R.id.signalBar3);
            bar4 = itemView.findViewById(R.id.signalBar4);
        }

        void bind(@NonNull BluetoothDeviceItem item, @NonNull OnDeviceClickListener listener) {
            tvName.setText(item.getName());
            tvAddress.setText(item.getAddress());
            int level = item.getSignalLevel();
            setBar(bar1, level >= 1);
            setBar(bar2, level >= 2);
            setBar(bar3, level >= 3);
            setBar(bar4, level >= 4);
            itemView.setOnClickListener(v -> listener.onDeviceClick(item));
        }

        private static void setBar(@NonNull View bar, boolean active) {
            bar.setBackgroundResource(active
                    ? R.drawable.bg_signal_bar_active
                    : R.drawable.bg_signal_bar_inactive);
        }
    }
}


