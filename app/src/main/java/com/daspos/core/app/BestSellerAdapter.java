package com.daspos.core.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.daspos.R;
import com.daspos.model.BestSellerItem;
import com.daspos.shared.util.CurrencyUtils;

import java.util.ArrayList;
import java.util.List;

public class BestSellerAdapter extends RecyclerView.Adapter<BestSellerAdapter.VH> {
    private final List<BestSellerItem> items = new ArrayList<>();
    private int startRank = 0;

    public void submit(List<BestSellerItem> list) {
        submit(list, 0);
    }

    public void submit(List<BestSellerItem> list, int startRank) {
        items.clear();
        if (list != null) items.addAll(list);
        this.startRank = Math.max(0, startRank);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_best_seller, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        BestSellerItem item = items.get(position);
        holder.tvRank.setText(String.valueOf(startRank + position + 1));
        holder.tvName.setText(item.getProductName());
        holder.tvQty.setText(holder.itemView.getContext().getString(R.string.best_seller_qty_format, item.getTotalQty()));
        holder.tvRevenue.setText(CurrencyUtils.formatRupiah(item.getTotalRevenue()));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView tvRank;
        final TextView tvName;
        final TextView tvQty;
        final TextView tvRevenue;

        VH(@NonNull View itemView) {
            super(itemView);
            tvRank = itemView.findViewById(R.id.tvBestSellerRank);
            tvName = itemView.findViewById(R.id.tvBestSellerName);
            tvQty = itemView.findViewById(R.id.tvBestSellerQty);
            tvRevenue = itemView.findViewById(R.id.tvBestSellerRevenue);
        }
    }
}
