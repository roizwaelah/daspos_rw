package com.daspos.feature.user;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.daspos.R;
import com.daspos.core.app.MainActivity;
import com.daspos.feature.auth.AuthSessionStore;
import com.daspos.model.User;
import com.daspos.shared.util.ViewUtils;
import com.daspos.ui.UiStateRenderer;
import com.daspos.ui.state.ListUiState;
import com.daspos.viewmodel.ViewModelFactoryHelper;

import java.util.List;

public class UserListFragment extends Fragment {
    private UserAdapter adapter;
    private UserViewModel viewModel;

    @Override
    public void onResume() {
        super.onResume();
        if (viewModel != null) viewModel.loadUsers();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_user_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View root, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(root, savedInstanceState);
        if (!AuthSessionStore.isAdmin(requireContext())) {
            ViewUtils.toast(requireActivity(), getString(R.string.admin_only_feature));
            closeSelf();
            return;
        }

        Toolbar toolbar = root.findViewById(R.id.toolbar);
        ViewUtils.setupBackToolbar(requireActivity(), toolbar, getString(R.string.user_list));

        final RecyclerView rv = root.findViewById(R.id.rvUsers);
        final View layoutState = root.findViewById(R.id.layoutUserState);
        final ProgressBar progress = root.findViewById(R.id.progressUser);
        final TextView tvState = root.findViewById(R.id.tvUserState);

        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new UserAdapter(new UserAdapter.Listener() {
            @Override public void onClick(User user) {
                ((MainActivity) UserListFragment.this.requireActivity()).navigateTo(EditUserFragment.newInstance(user.getUsername()));
            }
        });
        rv.setAdapter(adapter);

        viewModel = ViewModelFactoryHelper.get(requireActivity(), UserViewModel.class);
        viewModel.getUsersLiveData().observe(getViewLifecycleOwner(), new Observer<List<User>>() {
            @Override public void onChanged(List<User> users) { adapter.submit(users); }
        });
        viewModel.getUsersUiState().observe(getViewLifecycleOwner(), new Observer<ListUiState<User>>() {
            @Override public void onChanged(ListUiState<User> state) {
                UiStateRenderer.renderListState(state, rv, layoutState, progress, tvState, getString(R.string.loading));
            }
        });

        root.findViewById(R.id.fabAddUser).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { ((MainActivity) UserListFragment.this.requireActivity()).navigateTo(new AddUserFragment()); }
        });

        viewModel.loadUsers();
    }

    private void closeSelf() {
        if (getParentFragmentManager().getBackStackEntryCount() > 0) {
            getParentFragmentManager().popBackStack();
        } else {
            requireActivity().finish();
        }
    }
}
