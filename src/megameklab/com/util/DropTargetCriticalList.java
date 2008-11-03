/*
 * MegaMekLab - Copyright (C) 2008 
 * 
 * Original author - jtighe (torren@users.sourceforge.net)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * for more details.
 */

package megameklab.com.util;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Vector;

import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;

import megamek.common.BipedMech;
import megamek.common.CriticalSlot;
import megamek.common.Entity;
import megamek.common.LocationFullException;
import megamek.common.Mech;
import megamek.common.Mounted;
import megamek.common.WeaponType;

public class DropTargetCriticalList extends JList implements DropTargetListener, MouseListener {

    /**
     * 
     */
    private static final long serialVersionUID = 6847511182922982125L;
    private Mech unit;
    private RefreshListener refresh;

    public DropTargetCriticalList(Vector<String> vector, Mech unit, RefreshListener refresh) {
        super(vector);
        new DropTarget(this, this);
        this.unit = unit;
        this.refresh = refresh;
        this.addMouseListener(this);
    }

    public void dragEnter(DropTargetDragEvent dtde) {
    }

    public void dragExit(DropTargetEvent dte) {
    }

    public void dragOver(DropTargetDragEvent dtde) {
    }

    public void drop(DropTargetDropEvent dtde) {

        if (dtde.getSource() instanceof DropTarget) {
            int location = getCritLocation();
            Transferable t = dtde.getTransferable();
            dtde.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE | DnDConstants.ACTION_LINK);
            try {
                int externalEngineHS = unit.getEngine().integralHeatSinkCapacity();
                
                String mountName = (String) t.getTransferData(DataFlavor.stringFlavor);
                Mounted eq = null;
                for (Mounted mount : unit.getEquipment()) {
                    if (mount.getLocation() == Entity.LOC_NONE && mount.getType().getInternalName().equals(mountName)) {
                        if ( UnitUtil.isHeatSink(mount) && externalEngineHS-- > 0 ){
                            continue;
                        }
                        eq = mount;
                        break;
                    }
                }

                int totalCrits = UnitUtil.getCritsUsed(unit, eq.getType());
                if ( (eq.getType().isSpreadable() || eq.isSplitable()) && totalCrits > 1){
                    int critsUsed = 0;
                    int primaryLocation = location;
                    int nextLocation = unit.getTransferLocation(location);
                    int emptyCrits = unit.getEmptyCriticals(location)-1;
                    
                    //No big splitables in the head!
                    if ( nextLocation == Mech.LOC_DESTROYED || ( unit.getEmptyCriticals(location)+unit.getEmptyCriticals(nextLocation) < totalCrits )){
                        throw new LocationFullException(eq.getName() + " does not fit in " + unit.getLocationAbbr(location) + " on " + unit.getDisplayName());                    }
                    for ( ; critsUsed < totalCrits; critsUsed++ ){
                        unit.addEquipment(eq, location, false);
                        if ( critsUsed == emptyCrits){
                            location = nextLocation;
                            totalCrits -= critsUsed;
                            critsUsed = 0;
                        }
                    }
                    changeMountStatus(eq, primaryLocation, nextLocation, false);
                } else if ( UnitUtil.getHighestContinuousNumberOfCrits(unit, location) >= totalCrits ){
                    unit.addEquipment(eq, location, false);
                    changeMountStatus(eq, location, false);
                } else {
                    throw new LocationFullException(eq.getName() + " does not fit in " + unit.getLocationAbbr(location) + " on " + unit.getDisplayName());
                }
            } catch (LocationFullException lfe) {
                JOptionPane.showMessageDialog(this, lfe.getMessage(), "Location Full", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                ex.printStackTrace();
            }

        }

    }

    private void changeMountStatus(Mounted eq, int location, boolean rear) {
        changeMountStatus(eq, location, -1, rear);
    }
    
    private void changeMountStatus(Mounted eq, int location, int secondaryLocation, boolean rear) {

        UnitUtil.changeMountStatus(unit, eq, location, secondaryLocation, rear);
        
        if (refresh != null) {
            refresh.refreshAll();
        }
    }

    public void dropActionChanged(DropTargetDragEvent dtde) {
    }

    public void mouseClicked(MouseEvent e) {
    }

    public void mouseEntered(MouseEvent e) {
    }

    public void mouseExited(MouseEvent e) {
    }

    public void mousePressed(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1 || ( e.getButton() == MouseEvent.BUTTON3 && this.getSelectedIndex() >= 0 ) ) {

            Mounted mount = getMounted();
            int location = getCritLocation();
            JPopupMenu popup = new JPopupMenu();
            
            if (mount != null) {
                popup.setAutoscrolls(true);
                JMenuItem info = new JMenuItem("Remove " + mount.getName());
                info.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        removeCrit();
                    }
                });
                popup.add(info);

                if (mount.getType() instanceof WeaponType && mount.getLocation() != Mech.LOC_LARM && mount.getLocation() != Mech.LOC_RARM) {

                    if (!mount.isRearMounted()) {
                        info = new JMenuItem("Make "+mount.getName()+" Rear Facing");
                        info.addActionListener(new ActionListener() {
                            public void actionPerformed(ActionEvent e) {
                                changeWeaponFacing(true);
                            }
                        });
                        popup.add(info);
                    } else {
                        info = new JMenuItem("Make "+mount.getName()+" Forward Facing");
                        info.addActionListener(new ActionListener() {
                            public void actionPerformed(ActionEvent e) {
                                changeWeaponFacing(false);
                            }
                        });
                        popup.add(info);
                    }
                }
            }
            
            if ( unit instanceof BipedMech && (location == Mech.LOC_LARM || location == Mech.LOC_RARM) ) {
                popup.setAutoscrolls(true);
                if ( unit.getCritical(location,3) == null || unit.getCritical(location,3).getType() != CriticalSlot.TYPE_SYSTEM) {
                    JMenuItem info = new JMenuItem("Add Hand");
                    info.setActionCommand(Integer.toString(location));
                    info.addActionListener(new ActionListener() {
                        public void actionPerformed(ActionEvent e) {
                            addHand(Integer.parseInt(e.getActionCommand()));
                        }
                    }); 
                    popup.add(info);
                }else if ( unit.getCritical(location,3) != null && unit.getCritical(location,3).getType() == CriticalSlot.TYPE_SYSTEM ) {
                    JMenuItem info = new JMenuItem("Remove Hand");
                    info.setActionCommand(Integer.toString(location));
                    info.addActionListener(new ActionListener() {
                        public void actionPerformed(ActionEvent e) {
                            removeHand(Integer.parseInt(e.getActionCommand()));
                        }
                    });
                    popup.add(info);
                }

                if ( unit.getCritical(location,2) == null || unit.getCritical(location,2).getType() != CriticalSlot.TYPE_SYSTEM) {
                    JMenuItem info = new JMenuItem("Add Lower Arm");
                    info.setActionCommand(Integer.toString(location));
                    info.addActionListener(new ActionListener() {
                        public void actionPerformed(ActionEvent e) {
                            addArm(Integer.parseInt(e.getActionCommand()));
                        }
                    });
                    popup.add(info);
                }else if ( unit.getCritical(location,2) != null && unit.getCritical(location,2).getType() == CriticalSlot.TYPE_SYSTEM ){
                    JMenuItem info = new JMenuItem("Remove Lower Arm");
                    info.setActionCommand(Integer.toString(location));
                    info.addActionListener(new ActionListener() {
                        public void actionPerformed(ActionEvent e) {
                            removeArm(Integer.parseInt(e.getActionCommand()));
                        }
                    });
                    popup.add(info);
                }
            }
            
            if ( popup.getComponentCount() > 0 ){
                popup.show(this, e.getX(), e.getY());
            }

        }
    }

    public void mouseReleased(MouseEvent e) {
    }

    private Mounted getMounted() {
        CriticalSlot crit = getCrit();
        Mounted mount = null;
        try {
            if (crit != null && crit.getType() == CriticalSlot.TYPE_EQUIPMENT) {
                mount = unit.getEquipment(crit.getIndex());
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return mount;
    }

    private CriticalSlot getCrit() {
        int slot = this.getSelectedIndex();
        int location = getCritLocation();
        CriticalSlot crit = null;
        if (slot >= 0 && slot < unit.getNumberOfCriticals(location)) {
            crit = unit.getCritical(location, slot);
        }

        return crit;
    }

    private void removeCrit() {
        CriticalSlot crit = getCrit();
        Mounted mounted = getMounted();
        
        if ( mounted == null )
            return;

        UnitUtil.removeCriticals(unit, mounted);
        
        if (crit != null && crit.getType() == CriticalSlot.TYPE_EQUIPMENT) {
            changeMountStatus(mounted, Mech.LOC_NONE, false);
        }

    }

    private void changeWeaponFacing(boolean rear) {
        Mounted mount = getMounted();
        int location = getCritLocation();
        changeMountStatus(mount, location, rear);
    }

    private int getCritLocation() {
        return Integer.parseInt(this.getName());
    }
    
    
    private void addHand(int location ) {
        CriticalSlot cs = unit.getCritical(location, 3);
        
        if ( cs != null ){
            Mounted mount = unit.getEquipment(cs.getIndex());
            UnitUtil.removeCriticals(unit, mount);
            changeMountStatus(mount, Mech.LOC_NONE, false);
        }
        unit.setCritical(location, 3, new CriticalSlot(CriticalSlot.TYPE_SYSTEM, Mech.ACTUATOR_HAND));
        addArm(location);
    }
    
    private void removeHand(int location) {
        unit.setCritical(location, 3, null);
        if (refresh != null) {
            refresh.refreshAll();
        }
    }
    
    private void removeArm(int location) {
        unit.setCritical(location, 2, null);
        removeHand(location);
    }
    
    private void addArm(int location) {
        CriticalSlot cs = unit.getCritical(location, 2);
        
        if ( cs != null ){
            Mounted mount = unit.getEquipment(cs.getIndex());
            UnitUtil.removeCriticals(unit, mount);
            changeMountStatus(mount, Mech.LOC_NONE, false);
        }

        unit.setCritical(location, 2, new CriticalSlot(CriticalSlot.TYPE_SYSTEM, Mech.ACTUATOR_LOWER_ARM));
        if (refresh != null) {
            refresh.refreshAll();
        }
    }
}