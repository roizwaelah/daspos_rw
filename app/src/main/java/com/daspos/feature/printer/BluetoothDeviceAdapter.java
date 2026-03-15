package com.daspos.feature.printer;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.daspos.R;

import java.util.ArrayList;
import java.util.List;

public class BluetoothDeviceAdapter extends RecyclerView.Adapter<BluetoothDeviceAdapter.VH> {
    public static class DeviceItem {
        private final String displayName;
        private final String address;
        public DeviceItem(String displayName, String address) { this.displayName = displayName; this.address = address; }
        public String getDisplayName() { return displayName; }
        public String getAddress() { return address; }
    }
    public interface Listener { void onClick(DeviceItem device); }
    private final List<DeviceItem> items = new ArrayList<DeviceItem>();
    private final Listener listener;
    public BluetoothDeviceAdapter(Listener listener) { this.listener = listener; }
    public void submit(List<DeviceItem> list) { items.clear(); items.addAll(list); notifyDataSetChanged(); }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_setting, parent, false));
    }
    @Override public void onBindViewHolder(@NonNull VH holder, int position) {
        final DeviceItem item = items.get(position);
        holder.title.setText(item.getDisplayName() + " (" + item.getAddress() + ")");
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { listener.onClick(item); }
        });
    }
    @Override public int getItemCount() { return items.size(); }
    static class VH extends RecyclerView.ViewHolder {
        TextView title;
        VH(@NonNull View itemView) { super(itemView); title = itemView.findViewById(R.id.tvSettingTitle); }
    }
}
