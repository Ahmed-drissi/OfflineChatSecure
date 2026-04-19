package com.example.offlinechatsecure.adapters;

import android.content.res.ColorStateList;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.offlinechatsecure.R;
import com.example.offlinechatsecure.models.BluetoothDeviceItem;
import com.example.offlinechatsecure.utils.PeerProfileStore;

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
        private final TextView tvStatus;
        private final ImageView ivDeviceIcon;
        private final View bar1;
        private final View bar2;
        private final View bar3;
        private final View bar4;
        private final View signalStrengthView;

        DeviceViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvDeviceName);
            tvAddress = itemView.findViewById(R.id.tvDeviceAddress);
            tvStatus = itemView.findViewById(R.id.tvDeviceStatus);
            ivDeviceIcon = itemView.findViewById(R.id.ivDeviceIcon);
            bar1 = itemView.findViewById(R.id.signalBar1);
            bar2 = itemView.findViewById(R.id.signalBar2);
            bar3 = itemView.findViewById(R.id.signalBar3);
            bar4 = itemView.findViewById(R.id.signalBar4);
            signalStrengthView = itemView.findViewById(R.id.signalStrengthView);
        }

        void bind(@NonNull BluetoothDeviceItem item, @NonNull OnDeviceClickListener listener) {
            String displayName = PeerProfileStore.getDisplayName(
                    itemView.getContext(),
                    item.getAddress(),
                    item.getName()
            );
            tvName.setText(displayName);
            tvAddress.setText(item.getAddress());

            String photoUri = PeerProfileStore.getPhotoUri(itemView.getContext(), item.getAddress());
            applyPhoto(photoUri);

            if (item.isPaired()) {
                if (signalStrengthView != null) {
                    signalStrengthView.setVisibility(View.GONE);
                }
                if (tvStatus != null) {
                    boolean active = item.isAppReachable();
                    tvStatus.setVisibility(View.VISIBLE);
                    tvStatus.setText(active
                            ? R.string.device_status_active
                            : R.string.device_status_inactive);
                }
            } else {
                if (tvStatus != null) {
                    tvStatus.setVisibility(View.GONE);
                }
                if (signalStrengthView != null) {
                    signalStrengthView.setVisibility(View.VISIBLE);
                }
                int level = item.getSignalLevel();
                setBar(bar1, level >= 1);
                setBar(bar2, level >= 2);
                setBar(bar3, level >= 3);
                setBar(bar4, level >= 4);
            }

            itemView.setOnClickListener(v -> listener.onDeviceClick(item));
        }

        private void applyPhoto(String photoUri) {
            if (ivDeviceIcon == null) {
                return;
            }
            if (photoUri == null || photoUri.trim().isEmpty()) {
                ivDeviceIcon.setImageResource(R.drawable.ic_phone_line);
                int iconPadding = dpToPx(12);
                ivDeviceIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                ivDeviceIcon.setPadding(iconPadding, iconPadding, iconPadding, iconPadding);
                ImageViewCompat.setImageTintList(ivDeviceIcon, ColorStateList.valueOf(
                        ContextCompat.getColor(itemView.getContext(), R.color.app_primary)));
                return;
            }

            try {
                ImageViewCompat.setImageTintList(ivDeviceIcon, null);
                ivDeviceIcon.setScaleType(ImageView.ScaleType.CENTER_CROP);
                ivDeviceIcon.setPadding(0, 0, 0, 0);
                ivDeviceIcon.setImageURI(Uri.parse(photoUri));
            } catch (Exception ignored) {
                ivDeviceIcon.setImageResource(R.drawable.ic_phone_line);
                int iconPadding = dpToPx(12);
                ivDeviceIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                ivDeviceIcon.setPadding(iconPadding, iconPadding, iconPadding, iconPadding);
                ImageViewCompat.setImageTintList(ivDeviceIcon, ColorStateList.valueOf(
                        ContextCompat.getColor(itemView.getContext(), R.color.app_primary)));
            }
        }

        private int dpToPx(int dp) {
            float density = itemView.getResources().getDisplayMetrics().density;
            return Math.round(dp * density);
        }


        private static void setBar(@NonNull View bar, boolean active) {
            bar.setBackgroundResource(active
                    ? R.drawable.bg_signal_bar_active
                    : R.drawable.bg_signal_bar_inactive);
        }
    }
}


