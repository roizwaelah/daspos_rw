package com.daspos.feature.product;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.daspos.R;
import com.daspos.model.Product;
import com.daspos.shared.util.CurrencyUtils;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class ProductSearchAdapter extends RecyclerView.Adapter<ProductSearchAdapter.VH> {
    public interface Listener { void onAdd(Product product); }

    private final List<Product> items = new ArrayList<Product>();
    private final Listener listener;

    public ProductSearchAdapter(Listener listener) { this.listener = listener; }

    public void submit(List<Product> products) {
        items.clear();
        if (products != null) items.addAll(products);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_product_search, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        final Product p = items.get(position);
        holder.tvName.setText(p.getName());
        if (p.isTierPricingEnabled()) {
            holder.tvInfo.setText("Ecer " + CurrencyUtils.formatRupiah(p.getPriceEcer()) + " | Renteng " + CurrencyUtils.formatRupiah(p.getPriceRenteng()) + " | stok " + p.getStock() + " ecer");
        } else {
            holder.tvInfo.setText("Harga " + CurrencyUtils.formatRupiah(p.getPriceEcer()) + " | stok " + p.getStock());
        }
        holder.btnAdd.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { listener.onAdd(p); }
        });
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvInfo;
        MaterialButton btnAdd;
        VH(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvProductName);
            tvInfo = itemView.findViewById(R.id.tvProductInfo);
            btnAdd = itemView.findViewById(R.id.btnAddCart);
        }
    }
}

