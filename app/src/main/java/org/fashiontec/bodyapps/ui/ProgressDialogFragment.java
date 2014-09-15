package org.fashiontec.bodyapps.ui;

import android.app.Dialog;
import android.app.DialogFragment;
import android.app.ProgressDialog;
import android.os.Bundle;

import org.fashiontec.bodyapps.main.R;

/**
 * Fragment for a progress dialog.
 */
public class ProgressDialogFragment extends DialogFragment {

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        ProgressDialog progress = new ProgressDialog(getActivity());
        progress.setMessage(getString(R.string.progress_dialog_message));
        progress.setCanceledOnTouchOutside(false);
        return progress;
    }

    public static DialogFragment getInstance() {
        return new ProgressDialogFragment();
    }
}
