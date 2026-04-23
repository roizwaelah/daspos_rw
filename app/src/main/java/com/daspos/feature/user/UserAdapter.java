package com.daspos.feature.user;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.daspos.R;
import com.daspos.model.User;

import java.util.ArrayList;
import java.util.List;

public class UserAdapter extends RecyclerView.Adapter<UserAdapter.VH> {
    public interface Listener {
        void onClick(User user);
    }

    private final List<User> items = new ArrayList<User>();
    private final Listener listener;

    public UserAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<User> users) {
        items.clear();
        if (users != null) items.addAll(users);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_setting, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        final User user = items.get(position);
        holder.icon.setImageResource(R.drawable.ic_user_group);
        holder.title.setText(user.getUsername() + " - " + user.getRole());
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onClick(user);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView title;

        VH(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.imgSettingIcon);
            title = itemView.findViewById(R.id.tvSettingTitle);
        }
    }
}
