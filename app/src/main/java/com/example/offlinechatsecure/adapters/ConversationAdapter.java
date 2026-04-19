package com.example.offlinechatsecure.adapters;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
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
import com.example.offlinechatsecure.models.ConversationSummary;
import com.example.offlinechatsecure.utils.PeerProfileStore;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.ConversationViewHolder> {

    public interface OnConversationClickListener {
        void onConversationClick(@NonNull ConversationSummary summary);
    }

    private final List<ConversationSummary> items = new ArrayList<>();
    private final OnConversationClickListener listener;

    public ConversationAdapter(@NonNull OnConversationClickListener listener) {
        this.listener = listener;
    }

    public void submit(@NonNull List<ConversationSummary> conversations) {
        items.clear();
        items.addAll(conversations);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ConversationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_conversation, parent, false);
        return new ConversationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ConversationViewHolder holder, int position) {
        ConversationSummary summary = items.get(position);
        holder.bind(summary, listener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ConversationViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvPeer;
        private final TextView tvPreview;
        private final TextView tvTime;
        private final TextView tvCount;
        private final ImageView ivPeerPhoto;

        ConversationViewHolder(@NonNull View itemView) {
            super(itemView);
            tvPeer = itemView.findViewById(R.id.tvConversationPeer);
            tvPreview = itemView.findViewById(R.id.tvConversationPreview);
            tvTime = itemView.findViewById(R.id.tvConversationTime);
            tvCount = itemView.findViewById(R.id.tvConversationCount);
            ivPeerPhoto = itemView.findViewById(R.id.ivConversationPeerPhoto);
        }

        void bind(@NonNull ConversationSummary summary, @NonNull OnConversationClickListener listener) {
            String peerKey = summary.getPeerAddress();
            String defaultName = resolveBluetoothName(peerKey);
            String displayName = PeerProfileStore.getDisplayName(
                    itemView.getContext(),
                    peerKey,
                    defaultName
            );
            tvPeer.setText(displayName);
            if (summary.getMessageCount() <= 0) {
                tvPreview.setText(R.string.history_no_messages_yet);
                tvTime.setText("");
                tvCount.setText("");
            } else {
                tvPreview.setText(summary.getLastMessage());
                tvTime.setText(
                        DateFormat.getTimeInstance(DateFormat.SHORT)
                                .format(new Date(summary.getLastTimestamp()))
                );
                tvCount.setText(itemView.getContext().getString(
                        R.string.history_message_count,
                        summary.getMessageCount()
                ));
            }

            String photoUri = PeerProfileStore.getPhotoUri(itemView.getContext(), peerKey);
            applyPhoto(photoUri);

            itemView.setOnClickListener(v -> listener.onConversationClick(summary));
        }

        private void applyPhoto(String photoUri) {
            if (ivPeerPhoto == null) {
                return;
            }

            int padding = dpToPx(12);
            if (photoUri == null || photoUri.trim().isEmpty()) {
                ivPeerPhoto.setImageResource(R.drawable.ic_chat_line);
                ivPeerPhoto.setPadding(padding, padding, padding, padding);
                ImageViewCompat.setImageTintList(ivPeerPhoto, ColorStateList.valueOf(
                        ContextCompat.getColor(itemView.getContext(), R.color.app_primary)));
                return;
            }

            try {
                ImageViewCompat.setImageTintList(ivPeerPhoto, null);
                ivPeerPhoto.setPadding(0, 0, 0, 0);
                ivPeerPhoto.setImageURI(Uri.parse(photoUri));
            } catch (Exception ignored) {
                ivPeerPhoto.setImageResource(R.drawable.ic_chat_line);
                ivPeerPhoto.setPadding(padding, padding, padding, padding);
                ImageViewCompat.setImageTintList(ivPeerPhoto, ColorStateList.valueOf(
                        ContextCompat.getColor(itemView.getContext(), R.color.app_primary)));
            }
        }

        private int dpToPx(int dp) {
            float density = itemView.getResources().getDisplayMetrics().density;
            return Math.round(dp * density);
        }

        @NonNull
        private String resolveBluetoothName(@NonNull String peerAddress) {
            try {
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                if (adapter == null) {
                    return peerAddress;
                }

                BluetoothDevice device = adapter.getRemoteDevice(peerAddress);
                String name = device != null ? device.getName() : null;
                if (name != null && !name.trim().isEmpty()) {
                    return name;
                }
            } catch (IllegalArgumentException | SecurityException ignored) {
                // Fallback to stored address when remote lookup isn't available.
            }
            return peerAddress;
        }
    }
}
