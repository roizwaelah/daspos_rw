package com.daspos.feature.user;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.daspos.R;
import com.daspos.core.app.BaseActivity;
import com.daspos.model.User;
import com.daspos.shared.util.ViewUtils;
import com.daspos.ui.UiStateRenderer;
import com.daspos.ui.state.ListUiState;
import com.daspos.viewmodel.ViewModelFactoryHelper;

import java.util.List;

public class UserListActivity extends BaseActivity {
    private UserAdapter adapter;
    private UserViewModel viewModel;

    @Override protected void onResume() { super.onResume(); if (viewModel != null) viewModel.loadUsers(); }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_list);

        Toolbar toolbar = findViewById(R.id.toolbar);
        ViewUtils.setupBackToolbar(this, toolbar, getString(R.string.user_list));

        final RecyclerView rv = findViewById(R.id.rvUsers);
        final View layoutState = findViewById(R.id.layoutUserState);
        final ProgressBar progress = findViewById(R.id.progressUser);
        final TextView tvState = findViewById(R.id.tvUserState);

        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new UserAdapter(new UserAdapter.Listener() {
            @Override public void onClick(User user) {
                Intent intent = new Intent(UserListActivity.this, EditUserActivity.class);
                intent.putExtra("username", user.getUsername());
                startActivity(intent);
            }
        });
        rv.setAdapter(adapter);

        viewModel = ViewModelFactoryHelper.get(this, UserViewModel.class);
        viewModel.getUsersLiveData().observe(this, new Observer<List<User>>() {
            @Override public void onChanged(List<User> users) { adapter.submit(users); }
        });
        viewModel.getUsersUiState().observe(this, new Observer<ListUiState<User>>() {
            @Override public void onChanged(ListUiState<User> state) {
                UiStateRenderer.renderListState(state, rv, layoutState, progress, tvState, getString(R.string.loading));
            }
        });

        findViewById(R.id.fabAddUser).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(UserListActivity.this, AddUserActivity.class)); }
        });

        viewModel.loadUsers();
    }
}
