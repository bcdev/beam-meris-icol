package org.esa.beam.meris.icol.ui;

import org.esa.beam.framework.ui.ModelessDialog;
import org.esa.beam.framework.ui.command.CommandEvent;
import org.esa.beam.visat.actions.AbstractVisatAction;

/**
 * Action for ICOL.
 *
 * @author Marco Zuehlke
 */
public class IcolAction extends AbstractVisatAction {
	
    private ModelessDialog dialog;
    
	@Override
    public void actionPerformed(CommandEvent commandEvent) {
	    if (dialog == null) {
            final String helpId = "icolProcessor";
            dialog = new IcolDialog(getAppContext(), helpId);
        }
        dialog.show();
    }
}