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

public class DeviceListAdapter extends RecyclerView.Adapter<DeviceListAdapter.DeviceViewHolder> {

    public interface OnDeviceClickListener {
        void onDeviceClick(@NonNull BluetoothDeviceItem item);
    }

    private final List<BluetoothDeviceItem> devices = new ArrayList<>();
    private final OnDeviceClickListener onDeviceClickListener;

    public DeviceListAdapter(@NonNull OnDeviceClickListener onDeviceClickListener) {
        this.onDeviceClickListener = onDeviceClickListener;
    }

    public void setDevices(@NonNull List<BluetoothDeviceItem> updatedDevices) {
        devices.clear();
        devices.addAll(updatedDevices);
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
        BluetoothDeviceItem item = devices.get(position);
        holder.bind(item, onDeviceClickListener);
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }

    static class DeviceViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvName;
        private final TextView tvAddress;

        DeviceViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvDeviceName);
            tvAddress = itemView.findViewById(R.id.tvDeviceAddress);
        }

        void bind(@NonNull BluetoothDeviceItem item, @NonNull OnDeviceClickListener listener) {
            tvName.setText(item.getName());
            tvAddress.setText(item.getAddress());
            itemView.setOnClickListener(v -> listener.onDeviceClick(item));
        }
    }
}

