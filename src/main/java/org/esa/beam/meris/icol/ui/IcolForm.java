package org.esa.beam.meris.icol.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTabbedPane;
import javax.swing.border.TitledBorder;

import org.esa.beam.dataio.envisat.EnvisatProductReader;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.ui.SourceProductSelector;
import org.esa.beam.framework.gpf.ui.TargetProductSelector;
import org.esa.beam.framework.gpf.ui.TargetProductSelectorModel;
import org.esa.beam.framework.ui.AppContext;
import org.esa.beam.framework.ui.TableLayout;

import com.bc.ceres.binding.swing.BindingContext;


class IcolForm extends JTabbedPane {

    private static final String N1 = "N1";
    private JCheckBox rhoToa;
    private JCheckBox rhoToaRayleigh;
    private JCheckBox rhoToaAerosol;
    private JCheckBox aeRayleigh;
    private JCheckBox aeAerosol;
    private JCheckBox alphaAot;
    
    private JRadioButton icolAerosol;
    private JRadioButton userAerosol;
    private JFormattedTextField angstroemValue;
    private JFormattedTextField aotValue;
    private JRadioButton correctForBothButton;
    private TargetProductSelector targetProductSelector;
    private SourceProductSelector sourceProductSelector;
    private ComboBoxModel comboBoxModelRhoToa;
    private ComboBoxModel comboBoxModelN1;
    private JRadioButton correctForRayleighButton;
    private JRadioButton rhoToaProductTypeButton;
    private JRadioButton radianceProductTypeButton;
    private ButtonGroup productTypeGroup;
    private ButtonGroup aerosolGroup;
    private ButtonGroup radianceAEGroup;
    
	public IcolForm(AppContext appContext, IcolModel icolModel, TargetProductSelector targetProductSelector) {
        this.targetProductSelector = targetProductSelector;
        JComboBox targetFormatComboBox = targetProductSelector.getFormatNameComboBox();
	    comboBoxModelRhoToa = targetFormatComboBox.getModel();
	    comboBoxModelN1 = createN1ComboboxModel(targetFormatComboBox);
	    sourceProductSelector = new SourceProductSelector(appContext, "Input-Product:");
        initComponents();
        JComboBox sourceComboBox = sourceProductSelector.getProductNameComboBox();
        sourceComboBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                updateProductTypeSettings();
            }
        });
        targetFormatComboBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                updateProductFormatChange();
            }
        });
		bindComponents(icolModel);
		updateAerosolUIstate();
    }
	
	private ComboBoxModel createN1ComboboxModel(JComboBox targetFormatComboBox) {
	    DefaultComboBoxModel comboBoxModel = new DefaultComboBoxModel();
	    comboBoxModel.addElement(N1);
	    int itemCount = targetFormatComboBox.getItemCount();
	    for (int i = 0; i < itemCount; i++) {       
	        comboBoxModel.addElement(targetFormatComboBox.getItemAt(i));
        }
        return comboBoxModel;
	}
	
	public void prepareShow() {
	    sourceProductSelector.initProducts();
        updateProductTypeSettings();
    }
	
	public void prepareHide() {
	    sourceProductSelector.releaseProducts();
    }
	
    private void bindComponents(IcolModel icolModel) {
        final BindingContext bc = new BindingContext(icolModel.getValueContainer());
        bc.bind("exportRhoToa", rhoToa);
        bc.bind("exportRhoToaRayleigh", rhoToaRayleigh);
        bc.bind("exportRhoToaAerosol", rhoToaAerosol);
        bc.bind("exportAeRayleigh", aeRayleigh);
        bc.bind("exportAeAerosol", aeAerosol);
        bc.bind("exportAlphaAot", alphaAot);
		
        Map<AbstractButton, Object> aerosolGroupValueSet = new HashMap<AbstractButton, Object>(4);
        aerosolGroupValueSet.put(icolAerosol, false);
        aerosolGroupValueSet.put(userAerosol, true);
        bc.bind("useUserAlphaAndAot", aerosolGroup, aerosolGroupValueSet);
        bc.bind("userAlpha", angstroemValue);
        bc.bind("userAot", aotValue);
    	
        Map<AbstractButton, Object> radianceAEGroupValueSet = new HashMap<AbstractButton, Object>(4);
        radianceAEGroupValueSet.put(correctForRayleighButton, false);
        radianceAEGroupValueSet.put(correctForBothButton, true);
        bc.bind("correctForBoth", radianceAEGroup, radianceAEGroupValueSet);
    	
        bc.bind("productType", productTypeGroup);
    	bc.bind("sourceProduct", sourceProductSelector.getProductNameComboBox());
    }

    private void initComponents() {
        TableLayout layoutIO = new TableLayout(1);
        layoutIO.setTableAnchor(TableLayout.Anchor.NORTHWEST);
        layoutIO.setTableFill(TableLayout.Fill.HORIZONTAL);
        layoutIO.setTableWeightX(1);
        layoutIO.setCellWeightY(2, 0, 1);
        layoutIO.setTablePadding(2, 2);
        
        TableLayout layoutParam = new TableLayout(1);
        layoutParam.setTableAnchor(TableLayout.Anchor.NORTHWEST);
        layoutParam.setTableFill(TableLayout.Fill.HORIZONTAL);
        layoutParam.setTableWeightX(1);
        layoutParam.setCellWeightY(3, 0, 1);
        layoutParam.setTablePadding(2, 2);

        JPanel ioTab = new JPanel(layoutIO);
        JPanel paramTab = new JPanel(layoutParam);
        addTab("I/O Parameters", ioTab);
        addTab("Processing Parameters", paramTab);
        
        JPanel inputPanel = sourceProductSelector.createDefaultPanel();
        ioTab.add(inputPanel);
        JPanel productTypePanel = createProductTypePanel();
        ioTab.add(productTypePanel);
		ioTab.add(targetProductSelector.createDefaultPanel());
		ioTab.add(new JLabel(""));
		
        JPanel aerosolPanel = createAerosolPanel();
        paramTab.add(aerosolPanel);
        
        JPanel rhoToaPanel = createRhoToaBandSelectionPanel();
        paramTab.add(rhoToaPanel);
        
		JPanel n1Panel = createRadiancePanel();
		paramTab.add(n1Panel);
		paramTab.add(new JLabel(""));
    }

	private JPanel createRadiancePanel() {
		TableLayout layout = new TableLayout(1);
		layout.setTableAnchor(TableLayout.Anchor.WEST);
		layout.setTableFill(TableLayout.Fill.HORIZONTAL);
		layout.setTableWeightX(1);
		layout.setTablePadding(2, 2);
		JPanel panel = new JPanel(layout);

		panel.setBorder(BorderFactory.createTitledBorder(null, "Radiance Product",
                                                                TitledBorder.DEFAULT_JUSTIFICATION,
                                                                TitledBorder.DEFAULT_POSITION,
                                                                new Font("Tahoma", 0, 11),
                                                                new Color(0, 70, 213)));
		
		radianceAEGroup = new ButtonGroup();
        correctForRayleighButton = new JRadioButton("Correct for AE rayleigh");
        panel.add(correctForRayleighButton);
        radianceAEGroup.add(correctForRayleighButton);
		correctForBothButton = new JRadioButton("Correct for AE rayleigh and AE aerosol");
        correctForBothButton.setSelected(true);
        radianceAEGroup.add(correctForBothButton);
		panel.add(correctForBothButton);
		
		ButtonGroup n1Group = new ButtonGroup();
		n1Group.add(correctForRayleighButton);
		n1Group.add(correctForBothButton);
		
		return panel;
	}
	
	private void setRadiancePanelEnabled(boolean enabled) {
	    correctForRayleighButton.setEnabled(enabled);
	    correctForBothButton.setEnabled(enabled);
    }

	private JPanel createRhoToaBandSelectionPanel() {
		TableLayout layout = new TableLayout(1);
		layout.setTableAnchor(TableLayout.Anchor.WEST);
		layout.setTableFill(TableLayout.Fill.HORIZONTAL);
		layout.setTableWeightX(1);
		layout.setTablePadding(2, 2);
		
		rhoToa = new JCheckBox("TOA reflectances (rho_toa)");
        rhoToaRayleigh = new JCheckBox("TOA reflectances corrected for AE rayleigh (rho_toa_AERC)");
        rhoToaAerosol = new JCheckBox("TOA reflectances corrected for AE rayleigh and AE aerosol (rho_toa_AEAC)");
        aeRayleigh = new JCheckBox("AE rayleigh correction term (rho_aeRay)");
        aeAerosol = new JCheckBox("AE aerosol correction term (rho_aeAer)");
        alphaAot = new JCheckBox("alpha + aot");
        
		JPanel panel = new JPanel(layout);
		panel.setBorder(BorderFactory.createTitledBorder(null,
				"RhoToa-Product", TitledBorder.DEFAULT_JUSTIFICATION,
				TitledBorder.DEFAULT_POSITION, new Font("Tahoma", 0, 11),
				new Color(0, 70, 213)));

		panel.add(new JLabel("Bands included in the RhoToa product:"));
        panel.add(rhoToa);
        panel.add(rhoToaRayleigh);
        panel.add(rhoToaAerosol);
        panel.add(aeRayleigh);
        panel.add(aeAerosol);
        panel.add(alphaAot);

		return panel;
	}
	
	private void setRhoToaBandSelectionPanelEnabled(boolean enabled) {
	    rhoToa.setEnabled(enabled);
	    rhoToaRayleigh.setEnabled(enabled);
	    rhoToaAerosol.setEnabled(enabled);
	    aeRayleigh.setEnabled(enabled);
	    aeAerosol.setEnabled(enabled);
	    alphaAot.setEnabled(enabled);
	}

	private JPanel createAerosolPanel() {
		TableLayout layout = new TableLayout(3);
        layout.setTableAnchor(TableLayout.Anchor.WEST);
        layout.setTableFill(TableLayout.Fill.HORIZONTAL);
        layout.setColumnWeightX(0, 0.1);
        layout.setColumnWeightX(1, 0.1);
        layout.setColumnWeightX(2, 1);
        layout.setCellColspan(0, 0, 3);
        layout.setCellColspan(1, 0, 3);
        layout.setTablePadding(2, 2);
        layout.setCellPadding(2, 0, new Insets(0, 24, 0, 0));
        layout.setCellPadding(3, 0, new Insets(0, 24, 0, 0));
		JPanel panel = new JPanel(layout);

		panel.setBorder(BorderFactory.createTitledBorder(null, "Aerosol",
                                                                TitledBorder.DEFAULT_JUSTIFICATION,
                                                                TitledBorder.DEFAULT_POSITION,
                                                                new Font("Tahoma", 0, 11),
                                                                new Color(0, 70, 213)));
		aerosolGroup = new ButtonGroup();
        icolAerosol = new JRadioButton("Computed by AE algorithm");
        icolAerosol.setSelected(true);
		panel.add(icolAerosol);
		aerosolGroup.add(icolAerosol);
		
		userAerosol = new JRadioButton("User supplied:");
        panel.add(userAerosol);
		aerosolGroup.add(userAerosol);

		angstroemValue = new JFormattedTextField();
        aotValue = new JFormattedTextField();
        
		panel.add(new JLabel("Angstroem: "));
        panel.add(angstroemValue);
		panel.add(new JPanel());
		
		panel.add(new JLabel("AOT: "));
		panel.add(aotValue);
		panel.add(new JPanel());
		
		ActionListener aerosolActionListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                updateAerosolUIstate();
            }
		};
        icolAerosol.addActionListener(aerosolActionListener);
        userAerosol.addActionListener(aerosolActionListener);
		
		return panel;
	}
	
	private void updateAerosolUIstate() {
        boolean selected = userAerosol.isSelected();
        angstroemValue.setEnabled(selected);
        aotValue.setEnabled(selected);
    }	
	
	private JPanel createProductTypePanel() {
	    TableLayout layout = new TableLayout(1);
        layout.setTableAnchor(TableLayout.Anchor.WEST);
        layout.setTableFill(TableLayout.Fill.HORIZONTAL);
        layout.setTableWeightX(1);
        
        JPanel panel = new JPanel(layout);
        panel.setBorder(BorderFactory.createTitledBorder(null, "Product Type Selection",
                TitledBorder.DEFAULT_JUSTIFICATION,
                TitledBorder.DEFAULT_POSITION,
                new Font("Tahoma", 0, 11),
                new Color(0, 70, 213)));
        
        rhoToaProductTypeButton = new JRadioButton("Compute RhoToa Product");
        rhoToaProductTypeButton.setSelected(true);
        panel.add(rhoToaProductTypeButton);
        radianceProductTypeButton = new JRadioButton("Compute Radiance Product");
        panel.add(radianceProductTypeButton);
        
        productTypeGroup = new ButtonGroup();
        productTypeGroup.add(rhoToaProductTypeButton);
        productTypeGroup.add(radianceProductTypeButton);
        
        ActionListener productTypeListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                updateProductTypeSettings();
            }
        };
        rhoToaProductTypeButton.addActionListener(productTypeListener);
        radianceProductTypeButton.addActionListener(productTypeListener);
        
        return panel;
	}
	
	private void updateProductTypeSettings() {
	    JComboBox formatNameComboBox = targetProductSelector.getFormatNameComboBox();
        if (rhoToaProductTypeButton.isSelected()) {
	        formatNameComboBox.setModel(comboBoxModelRhoToa);
	        setRhoToaBandSelectionPanelEnabled(true);
	        setRadiancePanelEnabled(false);
	    } else {
	        // Radiance product
	        boolean n1 = false;
            Product sourceProduct = sourceProductSelector.getSelectedProduct();
            if (sourceProduct != null) {
                File fileLocation = sourceProduct.getFileLocation();
                ProductReader productReader = sourceProduct.getProductReader();
                if (fileLocation != null &&
                        fileLocation.getName().endsWith("N1") &&
                        productReader instanceof EnvisatProductReader) {
                    n1 = true;
                }
            }
            if (n1) {
                formatNameComboBox.setModel(comboBoxModelN1);
            } else {
                formatNameComboBox.setModel(comboBoxModelRhoToa);
            }
            setRhoToaBandSelectionPanelEnabled(false);
            setRadiancePanelEnabled(true);
	    }
        Product sourceProduct = sourceProductSelector.getSelectedProduct();
        final TargetProductSelectorModel selectorModel = targetProductSelector.getModel();
        if (sourceProduct != null) {
            String sourceProductName = sourceProduct.getName();
            if (sourceProductName.endsWith(".N1")) {
                sourceProductName = sourceProductName.substring(0, sourceProductName.length() - 3);
            }
            if (rhoToaProductTypeButton.isSelected()) {
                selectorModel.setProductName("L1R_" + sourceProductName);
            } else {
                selectorModel.setProductName("L1N_" + sourceProductName);
            }
        } else {
            selectorModel.setProductName("icol");
        }
    }
	
	private void updateProductFormatChange() {
	    JComboBox formatNameComboBox = targetProductSelector.getFormatNameComboBox();
	    String selectedItem = (String) formatNameComboBox.getSelectedItem();
	    if (selectedItem.equals(N1)) {
	        JCheckBox saveToFileCheckBox = targetProductSelector.getSaveToFileCheckBox();
            saveToFileCheckBox.setSelected(true);
            saveToFileCheckBox.setEnabled(false);
            
            radianceProductTypeButton.setSelected(true);
            rhoToaProductTypeButton.setEnabled(false);
	    } else {
	        targetProductSelector.setEnabled(true);
	        
	        rhoToaProductTypeButton.setEnabled(true);
	    }
	}
	
}
