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

import java.util.ArrayList;
import java.util.List;

public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.VH> {
    public interface Listener {
        void onEdit(Product product);
        void onDelete(Product product);
    }

    private final List<Product> items = new ArrayList<Product>();
    private final Listener listener;

    public ProductAdapter(Listener listener) { this.listener = listener; }

    public void submit(List<Product> products) {
        items.clear();
        if (products != null) items.addAll(products);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_product, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        final Product p = items.get(position);
        holder.tvName.setText(p.getName());
        holder.tvInfo.setText(CurrencyUtils.formatRupiah(p.getPrice()) + " • stok " + p.getStock());
        holder.btnEdit.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { listener.onEdit(p); }
        });
        holder.btnDelete.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { listener.onDelete(p); }
        });
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvInfo;
        View btnEdit, btnDelete;
        VH(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvProductName);
            tvInfo = itemView.findViewById(R.id.tvProductInfo);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}
