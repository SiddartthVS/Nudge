import React, { useState } from 'react';
import { Modal, Pressable, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { Colors } from '../scripts/colors';

export type DropdownOption = {
  label: string;
  value: number;
};

type Props = {
  options: DropdownOption[];
  value: number;
  onChange: (value: number) => void;
};

export const RangeDropdown = ({ options, value, onChange }: Props) => {
  const [open, setOpen] = useState(false);
  const selected = options.find(option => option.value === value) ?? options[0];

  return (
    <>
      {/* --- TRIGGER --- */}
      <TouchableOpacity style={styles.trigger} onPress={() => setOpen(true)}>
        <Text style={styles.triggerLabel}>{selected.label}</Text>
        <Text style={styles.triggerArrow}>▾</Text>
      </TouchableOpacity>

      {/* --- MENU --- */}
      <Modal visible={open} transparent animationType="fade" onRequestClose={() => setOpen(false)}>
        <Pressable style={styles.backdrop} onPress={() => setOpen(false)}>
          <View style={styles.menu}>
            {options.map(option => (
              <TouchableOpacity
                key={option.value}
                style={styles.menuItem}
                onPress={() => {
                  onChange(option.value);
                  setOpen(false);
                }}
              >
                <Text
                  style={[
                    styles.menuItemLabel,
                    option.value === value && styles.menuItemLabelActive,
                  ]}
                >
                  {option.label}
                </Text>
              </TouchableOpacity>
            ))}
          </View>
        </Pressable>
      </Modal>
    </>
  );
};

const styles = StyleSheet.create({
  trigger: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: 'rgba(255,255,255,0.08)',
    borderRadius: 999,
    paddingHorizontal: 12,
    paddingVertical: 6,
  },
  triggerLabel: {
    color: Colors.text,
    fontFamily: 'WorkSans-Medium',
    fontSize: 13,
    marginRight: 4,
  },
  triggerArrow: {
    color: Colors.text,
    fontSize: 12,
  },
  backdrop: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.5)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  menu: {
    backgroundColor: Colors.grey,
    borderRadius: 16,
    paddingVertical: 8,
    width: 180,
  },
  menuItem: {
    paddingHorizontal: 20,
    paddingVertical: 12,
  },
  menuItemLabel: {
    color: Colors.text,
    fontFamily: 'WorkSans-Medium',
    fontSize: 15,
    opacity: 0.7,
  },
  menuItemLabelActive: {
    opacity: 1,
    fontFamily: 'WorkSans-SemiBold',
  },
});
