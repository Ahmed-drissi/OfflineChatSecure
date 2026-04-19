package com.example.offlinechatsecure.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.offlinechatsecure.R;
import com.example.offlinechatsecure.adapters.ConversationAdapter;
import com.example.offlinechatsecure.database.DBHelper;
import com.example.offlinechatsecure.models.ConversationSummary;

import java.util.List;

public class HistoryActivity extends AuthenticatedActivity {

    private DBHelper dbHelper;
    private ConversationAdapter adapter;
    private TextView tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        ImageButton btnBack = findViewById(R.id.btnHistoryBack);
        RecyclerView rvConversations = findViewById(R.id.rvConversations);
        tvEmpty = findViewById(R.id.tvHistoryEmpty);

        btnBack.setOnClickListener(v -> finish());

        dbHelper = new DBHelper(this);
        adapter = new ConversationAdapter(this::openConversation);
        rvConversations.setLayoutManager(new LinearLayoutManager(this));
        rvConversations.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadConversations();
    }

    @Override
    protected void onDestroy() {
        if (dbHelper != null) {
            dbHelper.close();
            dbHelper = null;
        }
        super.onDestroy();
    }

    private void loadConversations() {
        List<ConversationSummary> conversations = dbHelper.getConversations();
        adapter.submit(conversations);
        tvEmpty.setVisibility(conversations.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void openConversation(@NonNull ConversationSummary summary) {
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_REMOTE_NAME, summary.getPeerAddress());
        intent.putExtra(ChatActivity.EXTRA_REMOTE_ADDRESS, summary.getPeerAddress());
        intent.putExtra(ChatActivity.EXTRA_READ_ONLY, true);
        startActivity(intent);
    }
}
