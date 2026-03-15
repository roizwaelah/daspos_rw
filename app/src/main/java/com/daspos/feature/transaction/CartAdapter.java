package com.daspos.feature.transaction;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.daspos.R;
import com.daspos.model.CartItem;
import com.daspos.shared.util.CurrencyUtils;

import java.util.ArrayList;
import java.util.List;

public class CartAdapter extends RecyclerView.Adapter<CartAdapter.VH> {
    private final List<CartItem> items = new ArrayList<>();

    public void submit(List<CartItem> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_cart, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        CartItem item = items.get(position);
        holder.tvName.setText(item.getProduct().getName());
        holder.tvQty.setText(item.getQty() + " x " + CurrencyUtils.formatRupiah(item.getProduct().getPrice()));
        holder.tvSubtotal.setText(CurrencyUtils.formatRupiah(item.getSubtotal()));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvQty, tvSubtotal;

        VH(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvCartName);
            tvQty = itemView.findViewById(R.id.tvCartInfo);
            tvSubtotal = itemView.findViewById(R.id.tvCartSubtotal);
        }
    }
}
