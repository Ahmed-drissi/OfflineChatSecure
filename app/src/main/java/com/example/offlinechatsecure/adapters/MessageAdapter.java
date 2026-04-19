package com.example.offlinechatsecure.adapters;

import android.net.Uri;
import android.webkit.MimeTypeMap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.offlinechatsecure.R;
import com.example.offlinechatsecure.models.ChatMessage;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_LEFT = 0;
    private static final int VIEW_TYPE_RIGHT = 1;

    private final List<ChatMessage> items = new ArrayList<>();
    private int lastAnimatedPosition = -1;
    private OnMessageClickListener onMessageClickListener;

    public interface OnMessageClickListener {
        void onMessageClicked(@NonNull ChatMessage message);
    }

    public void setOnMessageClickListener(OnMessageClickListener listener) {
        this.onMessageClickListener = listener;
    }

    public void submitMessages(@NonNull List<ChatMessage> messages) {
        items.clear();
        items.addAll(messages);
        lastAnimatedPosition = messages.size() - 1;
        notifyDataSetChanged();
    }

    public void addMessage(@NonNull ChatMessage message) {
        items.add(message);
        notifyItemInserted(items.size() - 1);
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).isSentByMe() ? VIEW_TYPE_RIGHT : VIEW_TYPE_LEFT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_RIGHT) {
            View view = inflater.inflate(R.layout.item_message_right, parent, false);
            return new MessageViewHolder(view);
        }

        View view = inflater.inflate(R.layout.item_message_left, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        int currentPosition = holder.getBindingAdapterPosition();
        if (currentPosition == RecyclerView.NO_POSITION) {
            return;
        }

        MessageViewHolder messageHolder = (MessageViewHolder) holder;
        ChatMessage message = items.get(currentPosition);
        boolean isImageAttachment = isImageAttachment(messageHolder, message);
        if (isImageAttachment) {
            messageHolder.tvImage.setVisibility(View.VISIBLE);
            messageHolder.tvImage.setImageURI(Uri.parse(message.getAttachmentUri()));
            messageHolder.tvMessage.setVisibility(View.GONE);
        } else {
            messageHolder.tvImage.setVisibility(View.GONE);
            messageHolder.tvImage.setImageDrawable(null);
            messageHolder.tvMessage.setVisibility(View.VISIBLE);
            messageHolder.tvMessage.setText(message.getText());
        }

        String time = DateFormat.getTimeInstance(DateFormat.SHORT)
                .format(new Date(message.getTimestamp()));
        messageHolder.tvTime.setText(time);

        holder.itemView.setOnClickListener(v -> {
            if (onMessageClickListener == null) {
                return;
            }
            int adapterPosition = holder.getBindingAdapterPosition();
            if (adapterPosition == RecyclerView.NO_POSITION) {
                return;
            }
            onMessageClickListener.onMessageClicked(items.get(adapterPosition));
        });

        if (currentPosition > lastAnimatedPosition) {
            holder.itemView.setAlpha(0f);
            holder.itemView.setTranslationY(22f);
            holder.itemView.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(220L)
                    .start();
            lastAnimatedPosition = currentPosition;
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private static class MessageViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvMessage;
        private final TextView tvTime;
        private final ImageView tvImage;

        MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tvMessageText);
            tvTime = itemView.findViewById(R.id.tvMessageTime);
            tvImage = itemView.findViewById(R.id.ivMessageImage);
        }
    }

    private boolean isImageAttachment(@NonNull MessageViewHolder holder, @NonNull ChatMessage message) {
        if (!message.hasAttachment()) {
            return false;
        }

        try {
            Uri uri = Uri.parse(message.getAttachmentUri());
            String type = holder.itemView.getContext().getContentResolver().getType(uri);
            if (type != null && type.startsWith("image/")) {
                return true;
            }

            String path = uri.toString();
            String extension = MimeTypeMap.getFileExtensionFromUrl(path);
            if (extension != null && !extension.trim().isEmpty()) {
                String guessed = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                        extension.toLowerCase()
                );
                return guessed != null && guessed.startsWith("image/");
            }
        } catch (Exception ignored) {
            return false;
        }

        return false;
    }
}

