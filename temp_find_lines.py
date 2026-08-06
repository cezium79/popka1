file_path = 'C:/Android/Ohrana-c7953c45/app/src/main/java/com/example/ohrana/CloudStorageManager.kt'

with open(file_path, 'r', encoding='utf-8') as f:
    lines = f.readlines()

footer_lines = []
staff_events_lines = []

for i, line in enumerate(lines):
    if '// Футер' in line:
        footer_lines.append(i)
    if 'staffEvents' in line:
        staff_events_lines.append(i)

print(f"staffEvents found at lines: {[l+1 for l in staff_events_lines]}")
print(f"// Футер found at lines: {[l+1 for l in footer_lines]}")

# We want to insert staff events table before the FIRST footer (which is after incidents in first function)
target_line = footer_lines[0]
print(f"\nTarget insertion point: before line {target_line + 1}")
print(f"\nContext (lines around target):")
for i in range(max(0, target_line - 5), min(len(lines), target_line + 5)):
        marker = " >>>" if i == target_line else "    "
        print(f"{marker} {i+1}: {lines[i].rstrip()}")
