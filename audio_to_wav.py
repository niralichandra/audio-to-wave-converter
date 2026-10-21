from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

AUDIO_EXTENSIONS = {
    ".mp3", ".wav", ".ogg", ".flac", ".m4a", ".aac", ".wma",
    ".opus", ".webm", ".mp4", ".mkv", ".avi", ".mov", ".flv"
}


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Convert audio files to WAV using ffmpeg."
    )
    parser.add_argument(
        "input",
        nargs="*",
        help="Path to one or more source audio files or directories. If not provided, a selection dialog will open.",
    )
    parser.add_argument(
        "-o",
        "--output",
        help="Optional output path. If converting multiple files, this is treated as the output directory.",
    )
    parser.add_argument(
        "--sample-rate",
        type=int,
        help="Optional output sample rate, for example 44100.",
    )
    parser.add_argument(
        "--channels",
        type=int,
        choices=(1, 2),
        help="Optional number of output channels: 1 for mono, 2 for stereo.",
    )
    parser.add_argument(
        "-t",
        "--threads",
        type=int,
        help="Number of threads for parallel conversion. Defaults to logical CPU core count.",
    )
    return parser


def select_input_type_gui() -> str | None:
    import tkinter as tk

    selected_type = [None]

    def on_folder():
        selected_type[0] = "folder"
        root.destroy()

    def on_files():
        selected_type[0] = "files"
        root.destroy()

    def on_cancel():
        root.destroy()

    root = tk.Tk()
    root.title("Select Input Type")
    root.geometry("350x150")
    root.resizable(False, False)
    root.focus_force()
    root.eval('tk::PlaceWindow . center')

    label = tk.Label(
        root,
        text="Select how you want to choose audio files:",
        font=("Arial", 11),
        pady=15
    )
    label.pack()

    btn_frame = tk.Frame(root)
    btn_frame.pack(fill="x", padx=20)

    btn_folder = tk.Button(
        btn_frame,
        text="Select Folder",
        command=on_folder,
        width=12,
        height=1,
        font=("Arial", 10)
    )
    btn_folder.pack(side="left", expand=True, padx=5)

    btn_files = tk.Button(
        btn_frame,
        text="Select Files",
        command=on_files,
        width=12,
        height=1,
        font=("Arial", 10)
    )
    btn_files.pack(side="left", expand=True, padx=5)

    btn_cancel = tk.Button(
        btn_frame,
        text="Cancel",
        command=on_cancel,
        width=10,
        height=1,
        font=("Arial", 10)
    )
    btn_cancel.pack(side="left", expand=True, padx=5)

    root.protocol("WM_DELETE_WINDOW", on_cancel)
    root.mainloop()

    return selected_type[0]


def select_files_via_gui() -> list[Path]:
    import tkinter as tk
    from tkinter import filedialog

    root = tk.Tk()
    root.withdraw()
    root.focus_force()
    file_paths = filedialog.askopenfilenames(
        title="Select Audio Files to Convert to WAV",
        filetypes=[
            (
                "Audio Files",
                " ".join(f"*{ext}" for ext in sorted(AUDIO_EXTENSIONS)),
            ),
            ("All Files", "*.*"),
        ],
    )
    root.destroy()
    return [Path(p) for p in file_paths]


def select_folder_via_gui() -> Path | None:
    import tkinter as tk
    from tkinter import filedialog

    root = tk.Tk()
    root.withdraw()
    root.focus_force()
    folder_path = filedialog.askdirectory(
        title="Select Folder Containing Audio Files"
    )
    root.destroy()
    return Path(folder_path) if folder_path else None


def prompt_clean_output_directory(output_dir: Path) -> bool | None:
    import tkinter as tk
    from tkinter import messagebox

    root = tk.Tk()
    root.withdraw()
    root.focus_force()
    response = messagebox.askyesnocancel(
        title="Clean Output Folder?",
        message=f"The output directory already contains files:\n{output_dir.resolve()}\n\nDo you want to clean (empty) this directory before converting?",
        icon=messagebox.WARNING
    )
    root.destroy()
    return response


def resolve_output_path(
    input_path: Path,
    output_arg: str | None,
    is_batch: bool,
    input_folder: Path | None = None
) -> Path:
    if not output_arg:
        # If we selected a folder, preserve the folder hierarchy inside "output" subfolder
        if input_folder:
            try:
                rel_path = input_path.relative_to(input_folder)
                return input_folder / "output" / rel_path.with_suffix(".wav")
            except ValueError:
                pass
        return input_path.parent / "output" / input_path.with_suffix(".wav").name

    out_path = Path(output_arg).expanduser().resolve()

    if is_batch:
        if input_folder:
            try:
                rel_path = input_path.relative_to(input_folder)
                return out_path / rel_path.with_suffix(".wav")
            except ValueError:
                pass
        return out_path / input_path.with_suffix(".wav").name
    else:
        if out_path.is_dir() or output_arg.endswith(("/", "\\")):
            return out_path / input_path.with_suffix(".wav").name
        return out_path


def convert_to_wav(
    input_path: Path, output_path: Path, sample_rate: int | None, channels: int | None
) -> None:
    ffmpeg_path = shutil.which("ffmpeg")
    if not ffmpeg_path:
        raise RuntimeError("ffmpeg is not installed or not available on PATH.")

    command = [ffmpeg_path, "-y", "-i", str(input_path)]

    if sample_rate:
        command.extend(["-ar", str(sample_rate)])
    if channels:
        command.extend(["-ac", str(channels)])

    command.append(str(output_path))

    result = subprocess.run(command, capture_output=True, text=True)
    if result.returncode != 0:
        error_output = result.stderr.strip() or result.stdout.strip()
        raise RuntimeError(f"Conversion failed.\n{error_output}")


def find_audio_files_in_dir(directory: Path, output_dir: Path | None = None) -> list[Path]:
    audio_files = []
    resolved_output_dir = output_dir.resolve() if output_dir else (directory / "output").resolve()

    # Exclude output dir from scan to avoid converting output files recursively
    for root, dirs, files in os.walk(directory):
        root_path = Path(root).resolve()
        dirs[:] = [
            d for d in dirs
            if d.lower() != "output" and (root_path / d) != resolved_output_dir
        ]
        for file in files:
            file_path = Path(root) / file
            if file_path.suffix.lower() in AUDIO_EXTENSIONS:
                audio_files.append(file_path)
    return audio_files


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()

    input_paths: list[Path] = []
    input_folder: Path | None = None

    # Resolve inputs: command-line arguments or GUI selection
    if args.input:
        # Check if the command line argument is a folder
        for p_str in args.input:
            p = Path(p_str).expanduser().resolve()
            if p.is_dir():
                input_folder = p
                # If there are multiple inputs and one is a folder, search files inside it
                out_dir = Path(args.output).expanduser().resolve() if args.output else p / "output"
                input_paths.extend(find_audio_files_in_dir(p, out_dir))
            elif p.is_file():
                input_paths.append(p)
            else:
                print(f"Warning: Input path not found: {p}", file=sys.stderr)
    else:
        # Run GUI selection flow
        choice = select_input_type_gui()
        if choice == "folder":
            selected_dir = select_folder_via_gui()
            if selected_dir:
                input_folder = selected_dir
                print(f"Scanning folder: {input_folder}...")
                
                # Temporarily resolve output path to exclude it during scan
                temp_output_dir = Path(args.output).expanduser().resolve() if args.output else input_folder / "output"
                input_paths = find_audio_files_in_dir(input_folder, temp_output_dir)
                print(f"Found {len(input_paths)} audio files.")
        elif choice == "files":
            input_paths = select_files_via_gui()

    if not input_paths:
        print("No files selected or found. Exiting.")
        return 0

    is_batch = len(input_paths) > 1

    # Resolve the output directory
    output_dir: Path | None = None
    if args.output:
        output_dir = Path(args.output).expanduser().resolve()
    elif input_folder:
        output_dir = input_folder / "output"
    else:
        # Check if all files have the same parent
        parents = {p.parent for p in input_paths}
        if len(parents) == 1:
            output_dir = list(parents)[0] / "output"

    # Prompt user to clean output directory if it exists and has contents
    if output_dir and output_dir.exists() and any(output_dir.iterdir()):
        clean_choice = prompt_clean_output_directory(output_dir)
        if clean_choice is None:
            print("Operation cancelled. Exiting.")
            return 0
        elif clean_choice is True:
            print(f"Cleaning output directory: {output_dir}...")
            try:
                for item in output_dir.iterdir():
                    if item.is_file():
                        item.unlink()
                    elif item.is_dir():
                        shutil.rmtree(item)
                print("Output directory cleaned successfully.")
            except Exception as e:
                print(f"Warning: Could not clean output directory completely: {e}", file=sys.stderr)

    total_count = len(input_paths)
    success_count = 0
    failure_count = 0
    completed_count = 0

    # Configure threading
    max_workers = args.threads or os.cpu_count() or 4
    start_time = time.time()

    def process_file(file_path: Path) -> tuple[Path, bool, str | None]:
        try:
            out_file_path = resolve_output_path(file_path, args.output, is_batch, input_folder)
        except Exception as exc:
            return file_path, False, f"Error resolving output path: {exc}"

        if file_path.resolve() == out_file_path.resolve():
            return file_path, False, "Input and output paths must be different"

        try:
            out_file_path.parent.mkdir(parents=True, exist_ok=True)
            convert_to_wav(file_path, out_file_path, args.sample_rate, args.channels)
            return file_path, True, None
        except Exception as exc:
            return file_path, False, str(exc)

    print(f"\nStarting conversion of {total_count} file{'s' if total_count != 1 else ''} using {max_workers} parallel threads...")
    if output_dir:
        print(f"Output directory: {output_dir}")

    try:
        with ThreadPoolExecutor(max_workers=max_workers) as executor:
            futures = {executor.submit(process_file, p): p for p in input_paths}
            
            for future in as_completed(futures):
                file_path, success, error_msg = future.result()
                completed_count += 1
                
                if success:
                    success_count += 1
                else:
                    failure_count += 1
                    # Clear line to print failure cleanly
                    sys.stdout.write("\r" + " " * 120 + "\r")
                    print(f"[{file_path.name}] Conversion failed: {error_msg}", file=sys.stderr)

                # Update progress bar
                percent = (completed_count / total_count) * 100
                elapsed = time.time() - start_time
                avg_time = elapsed / completed_count
                remaining_files = total_count - completed_count
                eta_seconds = int(avg_time * remaining_files)
                
                eta_str = time.strftime('%H:%M:%S', time.gmtime(eta_seconds))
                elapsed_str = time.strftime('%H:%M:%S', time.gmtime(int(elapsed)))
                
                bar_length = 20
                filled_length = int(round(bar_length * completed_count / float(total_count)))
                bar = '=' * filled_length + '-' * (bar_length - filled_length)
                
                display_name = file_path.name[:30] + "..." if len(file_path.name) > 33 else file_path.name
                sys.stdout.write(
                    f"\r[{completed_count}/{total_count}] |{bar}| {percent:.1f}% | "
                    f"Succeeded: {success_count} | Failed: {failure_count} | "
                    f"Elapsed: {elapsed_str} | ETA: {eta_str} | Current: {display_name:<33}"
                )
                sys.stdout.flush()

    except KeyboardInterrupt:
        print("\n\nConversion interrupted by user. Shutting down worker threads...")
        for f in futures:
            f.cancel()
        executor.shutdown(wait=False)
        print("Shutdown complete. Exiting.")
        return 1

    print(f"\n\nBatch conversion complete: {success_count} succeeded, {failure_count} failed.")
    if output_dir and success_count > 0:
        print(f"Output files saved to: {output_dir}")
    return 1 if failure_count > 0 else 0


if __name__ == "__main__":
    raise SystemExit(main())
