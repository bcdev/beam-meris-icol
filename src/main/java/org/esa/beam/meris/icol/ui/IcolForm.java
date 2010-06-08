package org.esa.beam.meris.icol.ui;

import com.bc.ceres.swing.TableLayout;
import com.bc.ceres.swing.binding.BindingContext;
import org.esa.beam.dataio.envisat.EnvisatProductReader;
import org.esa.beam.framework.dataio.ProductReader;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.ui.SourceProductSelector;
import org.esa.beam.framework.gpf.ui.TargetProductSelector;
import org.esa.beam.framework.gpf.ui.TargetProductSelectorModel;
import org.esa.beam.framework.ui.AppContext;
import org.esa.beam.meris.icol.AeArea;
import org.esa.beam.meris.icol.tm.TmConstants;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.HashMap;
import java.util.Map;


class IcolForm extends JTabbedPane {

    private static final String N1 = "N1";
    private JCheckBox rhoToa;
    private JCheckBox rhoToaRayleigh;
    private JCheckBox rhoToaAerosol;
    private JCheckBox aeRayleigh;
    private JCheckBox aeAerosol;
    private JCheckBox alphaAot;
    
    private JCheckBox icolAerosolForWaterCheckBox;
    private JCheckBox icolAerosolCase2CheckBox;
    private JRadioButton icolCtp;
    private JRadioButton userCtp;
    private JFormattedTextField ctpValue;
    private JFormattedTextField angstroemValue;
    private JFormattedTextField aotValue;
    private JRadioButton correctForBothButton;
    private TargetProductSelector targetProductSelector;
    private SourceProductSelector sourceProductSelector;
    private ComboBoxModel comboBoxModelRhoToa;
    private ComboBoxModel comboBoxModelN1;
    private JRadioButton correctForRayleighButton;
    private JRadioButton rhoToaProductTypeButton;
    private JRadioButton reflectanceProductTypeButton;
    private ButtonGroup productTypeGroup;
    private ButtonGroup ctpGroup;
    private ButtonGroup radianceAEGroup;
    private JCheckBox nestedConvolutionCheckBox;
    private JCheckBox openclConvolutionCheckBox;
    private JComboBox aeAreaComboBox;
    private int landsatResolutionValue;
    private ButtonGroup landsatResolutionGroup;
    private JRadioButton landsatResolution300Button;
    private JRadioButton landsatResolution1200Button;
    private JLabel userCtpLabel;
    private boolean userCtpSelected;
    private JFormattedTextField landsatStartTimeValue;
    private JFormattedTextField landsatStopTimeValue;
    private JFormattedTextField landsatOzoneContentValue;
    private JFormattedTextField landsatPSurfValue;
    private JFormattedTextField landsatTM60Value;
    private JCheckBox landsatComputeFlagSettingsOnly;
    private JCheckBox landsatComputeToTargetGridOnly;
    private JCheckBox upscaleToTMFR;

    private JCheckBox landsatCloudFlagApplyBrightnessFilterCheckBox;
    private JCheckBox landsatCloudFlagApplyNdsiFilterCheckBox;
    private JCheckBox landsatCloudFlagApplyTemperatureFilterCheckBox;
    private JCheckBox landsatCloudFlagApplyNdviFilterCheckBox;
    private JFormattedTextField cloudBrightnessThresholdValue;
    private JFormattedTextField cloudNdviThresholdValue;
    private JFormattedTextField cloudNdsiThresholdValue;
    private JFormattedTextField cloudTM6ThresholdValue;

    private JCheckBox landsatLandFlagApplyNdviFilterCheckBox;
    private JCheckBox landsatLandFlagApplyTemperatureFilterCheckBox;
    private JFormattedTextField landNdviThresholdValue;
    private JFormattedTextField landTM6ThresholdValue;
    private String landsatSeasonValue;
    private ButtonGroup landsatSeasonGroup;
    private JRadioButton landsatWinterButton;
    private JRadioButton landsatSummerButton;

    public IcolForm(AppContext appContext, IcolModel icolModel, TargetProductSelector targetProductSelector) {
        this.targetProductSelector = targetProductSelector;
        JComboBox targetFormatComboBox = targetProductSelector.getFormatNameComboBox();
	    comboBoxModelRhoToa = targetFormatComboBox.getModel();
	    comboBoxModelN1 = createN1ComboboxModel(targetFormatComboBox);
	    sourceProductSelector = new SourceProductSelector
                (appContext, "Input-Product (MERIS: L1b, Landsat 5 TM: L1G or 'Geometry'):");
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
        updateUIStates();
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
        final BindingContext bc = new BindingContext(icolModel.getPropertyContainer());
        bc.bind("exportRhoToa", rhoToa);
        bc.bind("exportRhoToaRayleigh", rhoToaRayleigh);
        bc.bind("exportRhoToaAerosol", rhoToaAerosol);
        bc.bind("exportAeRayleigh", aeRayleigh);
        bc.bind("exportAeAerosol", aeAerosol);
        bc.bind("exportAlphaAot", alphaAot);
		
        Map<AbstractButton, Object> ctpGroupValueSet = new HashMap<AbstractButton, Object>(4);
        ctpGroupValueSet.put(icolCtp, false);
        ctpGroupValueSet.put(userCtp, true);
        bc.bind("useUserCtp", ctpGroup, ctpGroupValueSet);
        bc.bind("userCtp", ctpValue);

        bc.bind("icolAerosolForWater", icolAerosolForWaterCheckBox);
        bc.bind("icolAerosolCase2", icolAerosolCase2CheckBox);
        bc.bind("userAlpha", angstroemValue);
        bc.bind("userAot", aotValue);
    	
        Map<AbstractButton, Object> radianceAEGroupValueSet = new HashMap<AbstractButton, Object>(4);
        radianceAEGroupValueSet.put(correctForRayleighButton, false);
        radianceAEGroupValueSet.put(correctForBothButton, true);
        bc.bind("correctForBoth", radianceAEGroup, radianceAEGroupValueSet);

        bc.bind("aeArea", aeAreaComboBox);

        bc.bind("reshapedConvolution", nestedConvolutionCheckBox);
        bc.bind("openclConvolution", openclConvolutionCheckBox);

        bc.bind("productType", productTypeGroup);
        bc.bind("sourceProduct", sourceProductSelector.getProductNameComboBox());

        bc.bind("landsatTargetResolution", landsatResolutionGroup);
        bc.bind("landsatStartTime", landsatStartTimeValue);
        bc.bind("landsatStopTime", landsatStopTimeValue);
        bc.bind("landsatUserOzoneContent", landsatOzoneContentValue);
        bc.bind("landsatUserPSurf", landsatPSurfValue);
        bc.bind("landsatUserTm60", landsatTM60Value);
        bc.bind("landsatComputeFlagSettingsOnly", landsatComputeFlagSettingsOnly);
        bc.bind("landsatComputeToTargetGridOnly", landsatComputeToTargetGridOnly);
        bc.bind("upscaleToTMFR", upscaleToTMFR);

        bc.bind("landsatCloudFlagApplyBrightnessFilter", landsatCloudFlagApplyBrightnessFilterCheckBox);
        bc.bind("landsatCloudFlagApplyNdviFilter", landsatCloudFlagApplyNdviFilterCheckBox);
        bc.bind("landsatCloudFlagApplyNdsiFilter", landsatCloudFlagApplyNdsiFilterCheckBox);
        bc.bind("landsatCloudFlagApplyTemperatureFilter", landsatCloudFlagApplyTemperatureFilterCheckBox);
        bc.bind("cloudBrightnessThreshold", cloudBrightnessThresholdValue);
        bc.bind("cloudNdviThreshold", cloudNdviThresholdValue);
        bc.bind("cloudNdsiThreshold", cloudNdsiThresholdValue);
        bc.bind("cloudTM6Threshold", cloudTM6ThresholdValue);

        bc.bind("landsatLandFlagApplyNdviFilter", landsatLandFlagApplyNdviFilterCheckBox);
        bc.bind("landsatLandFlagApplyTemperatureFilter", landsatLandFlagApplyTemperatureFilterCheckBox);
        bc.bind("landNdviThreshold", landNdviThresholdValue);
        bc.bind("landTM6Threshold", landTM6ThresholdValue);
        bc.bind("landsatSeason", landsatSeasonGroup);
    }

    private void initComponents() {
        setPreferredSize(new Dimension(600, 740));

        TableLayout layoutIO = new TableLayout(1);
        layoutIO.setTableAnchor(TableLayout.Anchor.NORTHWEST);
        layoutIO.setTableFill(TableLayout.Fill.HORIZONTAL);
        layoutIO.setTableWeightX(1);
        layoutIO.setCellWeightY(2, 0, 1);
        layoutIO.setTablePadding(2, 2);

        TableLayout processingParam = new TableLayout(1);
        processingParam.setTableAnchor(TableLayout.Anchor.NORTHWEST);
        processingParam.setTableFill(TableLayout.Fill.HORIZONTAL);
        processingParam.setTableWeightX(1);
        processingParam.setCellWeightY(3, 0, 1);
        processingParam.setTablePadding(2, 2);

        TableLayout merisParam = new TableLayout(1);
        merisParam.setTableAnchor(TableLayout.Anchor.NORTHWEST);
        merisParam.setTableFill(TableLayout.Fill.HORIZONTAL);
        merisParam.setTableWeightX(1);
        merisParam.setCellWeightY(0, 0, 1);
        merisParam.setTablePadding(2, 2);

        TableLayout landsatParam = new TableLayout(1);
        landsatParam.setTableAnchor(TableLayout.Anchor.NORTHWEST);
        landsatParam.setTableFill(TableLayout.Fill.HORIZONTAL);
        landsatParam.setTableWeightX(1);
        landsatParam.setCellWeightY(3, 0, 1);
        landsatParam.setTablePadding(2, 2);

        JPanel ioTab = new JPanel(layoutIO);
        JPanel processingParamTab = new JPanel(processingParam);
        JPanel merisParamTab = new JPanel(merisParam);
        JPanel landsatParamTab = new JPanel(landsatParam);
        addTab("I/O Parameters", ioTab);
        addTab("General Settings", processingParamTab);
        addTab("MERIS", merisParamTab);
        addTab("Landsat TM", landsatParamTab);

        JPanel inputPanel = sourceProductSelector.createDefaultPanel();
        ioTab.add(inputPanel);
        JPanel productTypePanel = createProductTypePanel();
        ioTab.add(productTypePanel);
		ioTab.add(targetProductSelector.createDefaultPanel());
		ioTab.add(new JLabel(""));

        JPanel processingPanel = createProcessingPanel();
        processingParamTab.add(processingPanel);
        ioTab.add(new JLabel(""));

        JPanel ctpPanel = createCTPPanel();
        merisParamTab.add(ctpPanel);

        JPanel aerosolPanel = createAerosolPanel();
        processingParamTab.add(aerosolPanel);

        JPanel rhoToaPanel = createRhoToaBandSelectionPanel();
        processingParamTab.add(rhoToaPanel);
        
		JPanel n1Panel = createRadiancePanel();
		processingParamTab.add(n1Panel);

        JPanel landsatProcessingPanel = createLandsatProcessingPanel();
		landsatParamTab.add(landsatProcessingPanel);

        JPanel landsatCloudFlagSettingPanel = createLandsatCloudFlagSettingPanel();
		landsatParamTab.add(landsatCloudFlagSettingPanel);

        JPanel landsatLandFlagSettingPanel = createLandsatLandFlagSettingPanel();
		landsatParamTab.add(landsatLandFlagSettingPanel);

		merisParamTab.add(new JLabel(""));
    }

	private JPanel createRadiancePanel() {
		TableLayout layout = new TableLayout(1);
		layout.setTableAnchor(TableLayout.Anchor.WEST);
		layout.setTableFill(TableLayout.Fill.HORIZONTAL);
		layout.setTableWeightX(1);
		layout.setTablePadding(2, 2);
		JPanel panel = new JPanel(layout);

		panel.setBorder(BorderFactory.createTitledBorder(null, "RhoToa Product / Radiance Product",
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
				"RhoToa Product", TitledBorder.DEFAULT_JUSTIFICATION,
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

    private JPanel createCTPPanel() {
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

		panel.setBorder(BorderFactory.createTitledBorder(null, "Cloud Top Pressure",
                                                                TitledBorder.DEFAULT_JUSTIFICATION,
                                                                TitledBorder.DEFAULT_POSITION,
                                                                new Font("Tahoma", 0, 11),
                                                                new Color(0, 70, 213)));
		ctpGroup = new ButtonGroup();
        icolCtp = new JRadioButton("Compute by algorithm");
        icolCtp.setSelected(true);
		panel.add(icolCtp);
		ctpGroup.add(icolCtp);

		userCtp = new JRadioButton("Use constant value");
        userCtp.setSelected(false);

        panel.add(userCtp);
		ctpGroup.add(userCtp);

		ctpValue = new JFormattedTextField("1013.0");

        userCtpLabel = new JLabel("CTP: ");
        panel.add(userCtpLabel);
        panel.add(ctpValue);
		panel.add(new JPanel());

		ActionListener ctpActionListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                updateCtpUIstate();
            }
		};
        icolCtp.addActionListener(ctpActionListener);
        userCtp.addActionListener(ctpActionListener);

		return panel;
	}

    private void updateUIStates() {
        updateCtpUIstate();
    }

    private void updateCtpUIstate() {
        userCtpSelected = userCtp.isSelected();
        userCtpLabel.setEnabled(userCtpSelected);
        ctpValue.setEnabled(userCtpSelected);
    }

    private JPanel createAerosolPanel() {
        TableLayout layout = new TableLayout(3);
        layout.setTableAnchor(TableLayout.Anchor.WEST);
        layout.setTableFill(TableLayout.Fill.HORIZONTAL);
        layout.setColumnWeightX(0, 0.1);
        layout.setColumnWeightX(1, 0.1);
        layout.setColumnWeightX(2, 1);
        layout.setTablePadding(2, 2);
        layout.setCellPadding(0, 0, new Insets(0, 24, 0, 0));
        layout.setCellPadding(1, 0, new Insets(0, 24, 0, 0));
        JPanel panel = new JPanel(layout);

        panel.setBorder(BorderFactory.createTitledBorder(null, "Aerosol Type Determination",
                                                                TitledBorder.DEFAULT_JUSTIFICATION,
                                                                TitledBorder.DEFAULT_POSITION,
                                                                new Font("Tahoma", 0, 11),
                                                                new Color(0, 70, 213)));

        angstroemValue = new JFormattedTextField();
        aotValue = new JFormattedTextField();

		panel.add(new JLabel("Angstroem: "));
        panel.add(angstroemValue);
		panel.add(new JPanel());

        panel.add(new JLabel("AOT: "));
        panel.add(aotValue);
		panel.add(new JPanel());

        icolAerosolForWaterCheckBox = new JCheckBox("Over water, compute aerosol type by AE algorithm");
        icolAerosolForWaterCheckBox.setSelected(false);
		panel.add(icolAerosolForWaterCheckBox);
        panel.add(new JPanel());

        return panel;
    }

    private JPanel createProcessingPanel() {
        // table layout with a third 'empty' column
        // todo: this is not nice! use GridBagLayout for more complex panels
		TableLayout layout = new TableLayout(2);
        layout.setTableAnchor(TableLayout.Anchor.WEST);
        layout.setTableFill(TableLayout.Fill.HORIZONTAL);
        layout.setColumnWeightX(0, 1);
        layout.setColumnWeightX(1, 0.1);
//        layout.setColumnWeightX(2, 1);
        layout.setTablePadding(2, 2);
//        layout.setCellPadding(0, 0, new Insets(0, 24, 0, 0));
//        layout.setCellPadding(1, 0, new Insets(0, 24, 0, 0));
//        layout.setCellPadding(2, 0, new Insets(0, 24, 0, 0));
//        layout.setCellPadding(3, 0, new Insets(0, 24, 0, 0));
//        layout.setCellPadding(4, 0, new Insets(0, 24, 0, 0));
        layout.setCellColspan(0, 0, 2);
        layout.setCellColspan(1, 0, 2);
        layout.setCellColspan(4, 0, 2);
		JPanel panel = new JPanel(layout);

		panel.setBorder(BorderFactory.createTitledBorder(null, "Processing",
                                                                TitledBorder.DEFAULT_JUSTIFICATION,
                                                                TitledBorder.DEFAULT_POSITION,
                                                                new Font("Tahoma", 0, 11),
                                                                new Color(0, 70, 213)));

        openclConvolutionCheckBox = new JCheckBox("Perform convolutions with OpenCL (for unique aerosol type only, GPU hardware required)");
        openclConvolutionCheckBox.setSelected(true);
		panel.add(openclConvolutionCheckBox);
//        panel.add(new JLabel());

        nestedConvolutionCheckBox = new JCheckBox("Use simplified convolution scheme");
        nestedConvolutionCheckBox.setSelected(true);
		panel.add(nestedConvolutionCheckBox);
//        panel.add(new JLabel());

        aeAreaComboBox = new JComboBox();
        aeAreaComboBox.setRenderer(new AeAreaRenderer());
        panel.add(new JLabel("Where to apply the AE algorithm:"));
        panel.add(new JLabel());
//        panel.add(new JLabel());
        panel.add(aeAreaComboBox);
        panel.add(new JLabel());
//        panel.add(new JLabel());

        icolAerosolCase2CheckBox = new JCheckBox("Consider case 2 waters in AE algorithm");
        icolAerosolCase2CheckBox.setSelected(false);
		panel.add(icolAerosolCase2CheckBox);
//        panel.add(new JLabel());

		return panel;
	}

    private JPanel createLandsatProcessingPanel() {
        TableLayout layout = new TableLayout(3);
        layout.setTableAnchor(TableLayout.Anchor.WEST);
        layout.setTableFill(TableLayout.Fill.HORIZONTAL);
        layout.setColumnWeightX(0, 0.1);
        layout.setColumnWeightX(1, 0.5);
        layout.setColumnWeightX(2, 1);
        layout.setTablePadding(2, 2);
        layout.setCellPadding(0, 0, new Insets(0, 24, 0, 0));
        layout.setCellPadding(1, 0, new Insets(0, 48, 0, 0));
        layout.setCellPadding(2, 0, new Insets(0, 48, 0, 0));
        layout.setCellPadding(3, 0, new Insets(0, 24, 0, 0));
        layout.setCellPadding(4, 0, new Insets(0, 24, 0, 0));
        layout.setCellPadding(5, 0, new Insets(0, 24, 0, 0));
        layout.setCellPadding(6, 0, new Insets(0, 24, 0, 0));
        layout.setCellPadding(7, 0, new Insets(0, 24, 0, 0));
        layout.setCellPadding(8, 0, new Insets(0, 24, 0, 0));
        layout.setCellPadding(9, 0, new Insets(0, 24, 0, 0));
        layout.setCellPadding(10, 0, new Insets(0, 24, 0, 0));
        layout.setCellPadding(11, 0, new Insets(0, 24, 0, 0));

		JPanel panel = new JPanel(layout);

		panel.setBorder(BorderFactory.createTitledBorder(null, "Processing",
                                                                TitledBorder.DEFAULT_JUSTIFICATION,
                                                                TitledBorder.DEFAULT_POSITION,
                                                                new Font("Tahoma", 0, 11),
                                                                new Color(0, 70, 213)));

        panel.add(new JLabel("Target product resolution:"));
        panel.add(new JLabel(""));
        panel.add(new JLabel(""));

        landsatResolution300Button = new JRadioButton("300 m");
        landsatResolution300Button.setSelected(true);
        panel.add(landsatResolution300Button);
        panel.add(new JLabel(""));
        panel.add(new JLabel(""));
        landsatResolution1200Button = new JRadioButton("1200 m");
        landsatResolution1200Button.setSelected(false);
        panel.add(landsatResolution1200Button);
        panel.add(new JLabel(""));
        panel.add(new JLabel(""));

        landsatResolutionGroup = new ButtonGroup();
        landsatResolutionGroup.add(landsatResolution300Button);
        landsatResolutionGroup.add(landsatResolution1200Button);

         ActionListener landsatResolutionListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                updateLandsatResolutionSettings();
            }
        };
        landsatResolution300Button.addActionListener(landsatResolutionListener);
        landsatResolution1200Button.addActionListener(landsatResolutionListener);

        panel.add(new JLabel("Start Time (dd-MMM-yyyy hh:mm:ss): "));
        landsatStartTimeValue = new JFormattedTextField();
        panel.add(landsatStartTimeValue);
        panel.add(new JLabel(""));

        panel.add(new JLabel("Stop Time (dd-MMM-yyyy hh:mm:ss): "));
        landsatStopTimeValue = new JFormattedTextField();
        panel.add(landsatStopTimeValue);
        panel.add(new JLabel(""));

        panel.add(new JLabel("Ozone content (cm atm): "));
        landsatOzoneContentValue = new JFormattedTextField(Double.toString(TmConstants.DEFAULT_OZONE_CONTENT));
        panel.add(landsatOzoneContentValue);
        panel.add(new JLabel(""));

        panel.add(new JLabel("Surface pressure (hPa): "));
        landsatPSurfValue = new JFormattedTextField(Double.toString(TmConstants.DEFAULT_SURFACE_PRESSURE));
        panel.add(landsatPSurfValue);
        panel.add(new JLabel(""));

        panel.add(new JLabel("Surface TM apparent remperature (K): "));
        landsatTM60Value = new JFormattedTextField(Double.toString(TmConstants.DEFAULT_SURFACE_TM_APPARENT_TEMPERATURE));
        panel.add(landsatTM60Value);
        panel.add(new JLabel(""));

        landsatComputeToTargetGridOnly = new JCheckBox("Compute 'Geometry' product only (scale to target grid)");
        landsatComputeToTargetGridOnly.setSelected(false);
		panel.add(landsatComputeToTargetGridOnly);
        panel.add(new JLabel(""));
        panel.add(new JLabel(""));

        landsatComputeFlagSettingsOnly = new JCheckBox("Compute flag settings product only");
        landsatComputeFlagSettingsOnly.setSelected(false);
		panel.add(landsatComputeFlagSettingsOnly);
        panel.add(new JLabel(""));
        panel.add(new JLabel(""));

        upscaleToTMFR = new JCheckBox("Upscale Radiance/RhoToa product to TM FR (30m)");
        upscaleToTMFR.setSelected(false);
		panel.add(upscaleToTMFR);
        panel.add(new JLabel(""));

		panel.add(new JPanel());

		return panel;
	}

    private JPanel createLandsatCloudFlagSettingPanel() {
        // table layout with a third 'empty' column
        // todo: this is not nice! use GridBagLayout for more complex panels
		TableLayout layout = new TableLayout(3);
        layout.setTableAnchor(TableLayout.Anchor.WEST);
        layout.setTableFill(TableLayout.Fill.HORIZONTAL);
        layout.setColumnWeightX(0, 0.1);
        layout.setColumnWeightX(1, 0.1);
        layout.setColumnWeightX(2, 1);
        layout.setTablePadding(2, 2);
        layout.setCellPadding(0, 0, new Insets(0, 24, 0, 0));
        layout.setCellPadding(1, 0, new Insets(0, 48, 0, 0));
        layout.setCellPadding(2, 0, new Insets(0, 24, 0, 0));
        layout.setCellPadding(3, 0, new Insets(0, 48, 0, 0));
        layout.setCellPadding(4, 0, new Insets(0, 24, 0, 0));
        layout.setCellPadding(5, 0, new Insets(0, 48, 0, 0));
        layout.setCellPadding(6, 0, new Insets(0, 24, 0, 0));
        layout.setCellPadding(7, 0, new Insets(0, 48, 0, 0));
		JPanel panel = new JPanel(layout);

		panel.setBorder(BorderFactory.createTitledBorder(null, "Cloud Flag Settings",
                                                                TitledBorder.DEFAULT_JUSTIFICATION,
                                                                TitledBorder.DEFAULT_POSITION,
                                                                new Font("Tahoma", 0, 11),
                                                                new Color(0, 70, 213)));

        landsatCloudFlagApplyBrightnessFilterCheckBox =
                new JCheckBox("Brightness flag (set if TM3 > BT)");
        landsatCloudFlagApplyBrightnessFilterCheckBox.setSelected(true);
        panel.add(landsatCloudFlagApplyBrightnessFilterCheckBox);
        panel.add(new JLabel());
        panel.add(new JLabel());

        cloudBrightnessThresholdValue = new JFormattedTextField(Double.toString(TmConstants.DEFAULT_BRIGHTNESS_THRESHOLD));
        panel.add(new JLabel("Brightness threshold BT: "));
        panel.add(cloudBrightnessThresholdValue);
		panel.add(new JLabel());

        landsatCloudFlagApplyNdviFilterCheckBox =
                new JCheckBox("NDVI flag (set if NDVI < NDVIT, with NDVI = (TM4 - TM3)/(TM4 + TM3))");
        landsatCloudFlagApplyNdviFilterCheckBox.setSelected(true);
        panel.add(landsatCloudFlagApplyNdviFilterCheckBox);
        panel.add(new JLabel());
        panel.add(new JLabel());

        cloudNdviThresholdValue = new JFormattedTextField(Double.toString(TmConstants.DEFAULT_NDVI_CLOUD_THRESHOLD));
        panel.add(new JLabel("NDVI threshold NDVIT: "));
        panel.add(cloudNdviThresholdValue);
		panel.add(new JLabel());

        landsatCloudFlagApplyNdsiFilterCheckBox =
                new JCheckBox("NDSI flag (set if NDSI < NDSIT, with NDSI = (TM2 - TM5)/(TM2 + TM5))");
        landsatCloudFlagApplyNdsiFilterCheckBox.setSelected(true);
        panel.add(landsatCloudFlagApplyNdsiFilterCheckBox);
        panel.add(new JLabel());
        panel.add(new JLabel());

        cloudNdsiThresholdValue = new JFormattedTextField(Double.toString(TmConstants.DEFAULT_NDSI_THRESHOLD));
        panel.add(new JLabel("NDSI threshold NDSIT: "));
        panel.add(cloudNdsiThresholdValue);
		panel.add(new JLabel());

        landsatCloudFlagApplyTemperatureFilterCheckBox =
                new JCheckBox("Temperature flag (set if TM6 < TM6T)");
        landsatCloudFlagApplyTemperatureFilterCheckBox.setSelected(true);
        panel.add(landsatCloudFlagApplyTemperatureFilterCheckBox);
        panel.add(new JLabel());
        panel.add(new JLabel());

        cloudTM6ThresholdValue = new JFormattedTextField(Double.toString(TmConstants.DEFAULT_TM6_CLOUD_THRESHOLD));
        panel.add(new JLabel("Temperature threshold TM6T (K): "));
        panel.add(cloudTM6ThresholdValue);
		panel.add(new JLabel());

		return panel;
	}

    private JPanel createLandsatLandFlagSettingPanel() {
        // table layout with a third 'empty' column
        // todo: this is not nice! use GridBagLayout for more complex panels
		TableLayout layout = new TableLayout(3);
        layout.setTableAnchor(TableLayout.Anchor.WEST);
        layout.setTableFill(TableLayout.Fill.HORIZONTAL);
        layout.setColumnWeightX(0, 0.1);
        layout.setColumnWeightX(1, 0.1);
        layout.setColumnWeightX(2, 1);
        layout.setTablePadding(2, 2);
        layout.setCellPadding(0, 0, new Insets(0, 24, 0, 0));
        layout.setCellPadding(1, 0, new Insets(0, 48, 0, 0));
        layout.setCellPadding(2, 0, new Insets(0, 24, 0, 0));
        layout.setCellPadding(3, 0, new Insets(0, 48, 0, 0));
        layout.setCellPadding(4, 0, new Insets(0, 48, 0, 0));
        layout.setCellPadding(5, 0, new Insets(0, 72, 0, 0));
        layout.setCellPadding(6, 0, new Insets(0, 72, 0, 0));
		JPanel panel = new JPanel(layout);

		panel.setBorder(BorderFactory.createTitledBorder(null, "Land Flag Settings",
                                                                TitledBorder.DEFAULT_JUSTIFICATION,
                                                                TitledBorder.DEFAULT_POSITION,
                                                                new Font("Tahoma", 0, 11),
                                                                new Color(0, 70, 213)));

        landsatLandFlagApplyNdviFilterCheckBox =
                new JCheckBox("NDVI flag (set if NDVI < NDVIT, with NDVI = (TM4 - TM3)/(TM4 + TM3))");
        landsatLandFlagApplyNdviFilterCheckBox.setSelected(true);
        panel.add(landsatLandFlagApplyNdviFilterCheckBox);
        panel.add(new JLabel());
        panel.add(new JLabel());

        landNdviThresholdValue = new JFormattedTextField(Double.toString(TmConstants.DEFAULT_NDVI_LAND_THRESHOLD));
        panel.add(new JLabel("NDVI threshold: "));
        panel.add(landNdviThresholdValue);
		panel.add(new JLabel());

        landsatLandFlagApplyTemperatureFilterCheckBox =
                new JCheckBox("Temperature flag (set if TM6 > TM6T (summer), TM6 < TM6T (winter))");
        landsatLandFlagApplyTemperatureFilterCheckBox.setSelected(true);
        panel.add(landsatLandFlagApplyTemperatureFilterCheckBox);
        panel.add(new JLabel());
        panel.add(new JLabel());

        landTM6ThresholdValue = new JFormattedTextField(Double.toString(TmConstants.DEFAULT_TM6_LAND_THRESHOLD));
        panel.add(new JLabel("Temperature threshold TM6T (K): "));
        panel.add(landTM6ThresholdValue);
		panel.add(new JLabel());

        panel.add(new JLabel("Season:"));
        panel.add(new JLabel(""));
        panel.add(new JLabel(""));

        landsatSummerButton = new JRadioButton(TmConstants.LAND_FLAGS_SUMMER);
        landsatSummerButton.setSelected(true);
        panel.add(landsatSummerButton);
        panel.add(new JLabel(""));
        panel.add(new JLabel(""));
        landsatWinterButton = new JRadioButton(TmConstants.LAND_FLAGS_WINTER);
        landsatWinterButton.setSelected(false);
        panel.add(landsatWinterButton);
        panel.add(new JLabel(""));
        panel.add(new JLabel(""));

        landsatSeasonGroup = new ButtonGroup();
        landsatSeasonGroup.add(landsatSummerButton);
        landsatSeasonGroup.add(landsatWinterButton);

         ActionListener landsatSeasonListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                updateLandsatSeasonSettings();
            }
        };
        landsatSummerButton.addActionListener(landsatSeasonListener);
        landsatWinterButton.addActionListener(landsatSeasonListener);

		return panel;
	}



    private void updateLandsatResolutionSettings() {
        if (landsatResolution300Button.isSelected())
        {
            landsatResolutionValue = 300;
            landsatResolution1200Button.setSelected(false);
        } else {
            landsatResolutionValue = 1200;
            landsatResolution1200Button.setSelected(true);
        }
    }

    private void updateLandsatSeasonSettings() {
        if (landsatWinterButton.isSelected())
        {
            landsatSeasonValue = TmConstants.LAND_FLAGS_WINTER;
            landsatSummerButton.setSelected(false);
        } else {
            landsatSeasonValue = TmConstants.LAND_FLAGS_SUMMER;
            landsatWinterButton.setSelected(false);
        }
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

        reflectanceProductTypeButton = new JRadioButton("Compute Radiance Product");
        reflectanceProductTypeButton.setSelected(true);
        panel.add(reflectanceProductTypeButton);
        rhoToaProductTypeButton = new JRadioButton("Compute RhoToa Product");
        rhoToaProductTypeButton.setSelected(false);
        panel.add(rhoToaProductTypeButton);

        productTypeGroup = new ButtonGroup();
        productTypeGroup.add(reflectanceProductTypeButton);
        productTypeGroup.add(rhoToaProductTypeButton);

        ActionListener productTypeListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                updateProductTypeSettings();
            }
        };
        rhoToaProductTypeButton.addActionListener(productTypeListener);
        reflectanceProductTypeButton.addActionListener(productTypeListener);
        
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
            
            reflectanceProductTypeButton.setSelected(true);
            rhoToaProductTypeButton.setEnabled(false);
	    } else {
	        targetProductSelector.setEnabled(true);
	        
	        rhoToaProductTypeButton.setEnabled(true);
	    }
	}

    private static class AeAreaRenderer extends DefaultListCellRenderer  {

        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            final Component cellRendererComponent =
                    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (cellRendererComponent instanceof JLabel && value instanceof AeArea) {
                final JLabel label = (JLabel) cellRendererComponent;
                final AeArea aeArea = (AeArea) value;
                label.setText(aeArea.getLabel());
            }

            return cellRendererComponent;
        }
    }
}
