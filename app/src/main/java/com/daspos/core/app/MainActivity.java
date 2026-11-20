package com.daspos.core.app;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import com.daspos.R;
import com.daspos.feature.transaction.StrukFragment;
import com.daspos.feature.transaction.TransactionFragment;
import com.daspos.shared.util.NotificationDialogHelper;

public class MainActivity extends BaseActivity {
    public static final String EXTRA_FRAGMENT = "extra_fragment";
    public static final String EXTRA_TRANSACTION_ID = "extra_transaction_id";
    public static final String EXTRA_AUTO_PRINT = "extra_auto_print";

    private static final String FRAGMENT_TRANSACTION = "transaction";
    private static final String FRAGMENT_STRUK = "struk";

    public static Intent transactionIntent(Context context) {
        return baseIntent(context).putExtra(EXTRA_FRAGMENT, FRAGMENT_TRANSACTION);
    }

    public static Intent strukIntent(Context context, String transactionId) {
        return strukIntent(context, transactionId, false);
    }

    public static Intent strukIntent(Context context, String transactionId, boolean autoPrint) {
        return baseIntent(context)
                .putExtra(EXTRA_FRAGMENT, FRAGMENT_STRUK)
                .putExtra(EXTRA_TRANSACTION_ID, transactionId)
                .putExtra(EXTRA_AUTO_PRINT, autoPrint);
    }

    private static Intent baseIntent(Context context) {
        return new Intent(context, MainActivity.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (savedInstanceState != null) return;

        String kind = getIntent().getStringExtra(EXTRA_FRAGMENT);
        Fragment fragment;
        if (FRAGMENT_STRUK.equals(kind)) {
            fragment = StrukFragment.newInstance(
                    getIntent().getStringExtra(EXTRA_TRANSACTION_ID),
                    getIntent().getBooleanExtra(EXTRA_AUTO_PRINT, false));
        } else if (FRAGMENT_TRANSACTION.equals(kind)) {
            fragment = new TransactionFragment();
        } else {
            fragment = new HomeFragment();
        }
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    public void navigateTo(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
    }

    @Override
    public void onBackPressed() {
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            super.onBackPressed();
            return;
        }
        NotificationDialogHelper.showWarningConfirmation(
                this,
                R.string.exit_confirmation_title,
                R.string.exit_confirmation_message,
                R.string.exit,
                () -> {
                    MainActivity.super.onBackPressed();
                    finishAffinity();
                }
        );
    }
}
