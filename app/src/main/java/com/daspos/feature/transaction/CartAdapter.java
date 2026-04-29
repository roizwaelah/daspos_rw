package com.daspos.feature.transaction;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatAutoCompleteTextView;
import androidx.recyclerview.widget.RecyclerView;

import com.daspos.R;
import com.daspos.model.CartItem;
import com.daspos.model.SalesUnit;
import com.daspos.shared.util.CurrencyUtils;

import java.util.ArrayList;
import java.util.List;

public class CartAdapter extends RecyclerView.Adapter<CartAdapter.VH> {
    public interface Listener {
        void onMinus(CartItem item);
        void onPlus(CartItem item);
        void onUnitChanged(CartItem item, String unitCode);
    }

    private final List<CartItem> items = new ArrayList<>();
    private final Listener listener;

    public CartAdapter(Listener listener) {
        this.listener = listener;
    }

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
        final CartItem item = items.get(position);
        boolean tierEnabled = item.getProduct() != null && item.getProduct().isTierPricingEnabled();
        holder.tvName.setText(item.getProduct().getName());
        if (tierEnabled) {
            holder.tvQty.setText(holder.itemView.getContext().getString(
                    R.string.cart_line_format,
                    item.getQty(),
                    SalesUnit.displayName(item.getUnitCode()),
                    CurrencyUtils.formatRupiah(item.getUnitPrice())
            ));
        } else {
            holder.tvQty.setText(holder.itemView.getContext().getString(
                    R.string.cart_line_format_no_unit,
                    item.getQty(),
                    CurrencyUtils.formatRupiah(item.getUnitPrice())
            ));
        }
        holder.tvSubtotal.setText(CurrencyUtils.formatRupiah(item.getSubtotal()));
        holder.bindUnit(item, tierEnabled, listener);
        holder.btnMinus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onMinus(item);
            }
        });
        holder.btnPlus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onPlus(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvQty, tvSubtotal;
        ImageButton btnMinus, btnPlus;
        AppCompatAutoCompleteTextView spUnit;
        private boolean bindingSpinner;

        VH(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvCartName);
            tvQty = itemView.findViewById(R.id.tvCartInfo);
            tvSubtotal = itemView.findViewById(R.id.tvCartSubtotal);
            btnMinus = itemView.findViewById(R.id.btnMinus);
            btnPlus = itemView.findViewById(R.id.btnPlus);
            spUnit = itemView.findViewById(R.id.spCartUnit);
            ArrayAdapter<String> unitAdapter = new ArrayAdapter<>(
                    itemView.getContext(),
                    android.R.layout.simple_list_item_1,
                    new String[]{"ECER", "RENTENG", "PAK", "KARTON"}
            );
            unitAdapter.setDropDownViewResource(android.R.layout.simple_list_item_1);
            spUnit.setAdapter(unitAdapter);
        }

        void bindUnit(CartItem item, boolean tierEnabled, Listener listener) {
            if (!tierEnabled) {
                bindingSpinner = true;
                spUnit.setText("", false);
                bindingSpinner = false;
                spUnit.setOnItemClickListener(null);
                spUnit.setOnClickListener(null);
                spUnit.setVisibility(View.GONE);
                return;
            }

            spUnit.setVisibility(View.VISIBLE);
            bindingSpinner = true;
            spUnit.setText(labelOf(indexOf(item.getUnitCode())), false);
            bindingSpinner = false;
            spUnit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    spUnit.showDropDown();
                }
            });
            spUnit.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    if (bindingSpinner) return;
                    if (listener == null) return;
                    String nextCode = codeOf(position);
                    if (SalesUnit.normalize(item.getUnitCode()).equals(nextCode)) return;
                    listener.onUnitChanged(item, nextCode);
                }
            });
        }

        private int indexOf(String unitCode) {
            String code = SalesUnit.normalize(unitCode);
            if (SalesUnit.RENTENG.equals(code)) return 1;
            if (SalesUnit.PAK.equals(code)) return 2;
            if (SalesUnit.KARTON.equals(code)) return 3;
            return 0;
        }

        private String codeOf(int index) {
            if (index == 1) return SalesUnit.RENTENG;
            if (index == 2) return SalesUnit.PAK;
            if (index == 3) return SalesUnit.KARTON;
            return SalesUnit.ECER;
        }

        private String labelOf(int index) {
            if (index == 1) return "RENTENG";
            if (index == 2) return "PAK";
            if (index == 3) return "KARTON";
            return "ECER";
        }
    }
}
