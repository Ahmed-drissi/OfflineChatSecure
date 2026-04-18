package com.example.offlinechatsecure.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

    public void submitMessages(@NonNull List<ChatMessage> messages) {
        items.clear();
        items.addAll(messages);
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
        MessageViewHolder messageHolder = (MessageViewHolder) holder;
        ChatMessage message = items.get(position);
        messageHolder.tvMessage.setText(message.getText());

        String time = DateFormat.getTimeInstance(DateFormat.SHORT)
                .format(new Date(message.getTimestamp()));
        messageHolder.tvTime.setText(time);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private static class MessageViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvMessage;
        private final TextView tvTime;

        MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tvMessageText);
            tvTime = itemView.findViewById(R.id.tvMessageTime);
        }
    }
}

