package org.fashiontec.bodyapps.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;

import org.fashiontec.bodyapps.main.R;

import static android.content.DialogInterface.*;

/**
 * Fragment for alert dialogs used by the app.
 */
public class AlertDialogFragment extends DialogFragment {

    private static final String ARG_MESSAGE = "message";

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        String message = getArguments().getString(ARG_MESSAGE);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setMessage(message)
            .setIcon(R.drawable.warning)
            .setCancelable(false)
            .setNeutralButton(getString(R.string.alert_dialog_dismiss), new OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    dialog.cancel();
                }
            });

        return builder.create();
    }

    /**
     * Create a new instance of alert dialog that will display a custom message.
     * @param message message to display
     * @return new instance of this class
     */
    public static DialogFragment getInstance(String message) {
        AlertDialogFragment frag = new AlertDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_MESSAGE, message);
        frag.setArguments(args);
        return frag;
    }
}
