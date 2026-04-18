package com.example.offlinechatsecure.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.offlinechatsecure.R;
import com.example.offlinechatsecure.models.ConversationSummary;

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

        ConversationViewHolder(@NonNull View itemView) {
            super(itemView);
            tvPeer = itemView.findViewById(R.id.tvConversationPeer);
            tvPreview = itemView.findViewById(R.id.tvConversationPreview);
            tvTime = itemView.findViewById(R.id.tvConversationTime);
            tvCount = itemView.findViewById(R.id.tvConversationCount);
        }

        void bind(@NonNull ConversationSummary summary, @NonNull OnConversationClickListener listener) {
            tvPeer.setText(summary.getPeerAddress());
            tvPreview.setText(summary.getLastMessage());
            tvTime.setText(
                    DateFormat.getTimeInstance(DateFormat.SHORT)
                            .format(new Date(summary.getLastTimestamp()))
            );
            tvCount.setText(itemView.getContext().getString(
                    R.string.history_message_count,
                    summary.getMessageCount()
            ));
            itemView.setOnClickListener(v -> listener.onConversationClick(summary));
        }
    }
}
