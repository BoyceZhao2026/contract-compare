declare module 'diff-match-patch' {
  export interface Diff {
    0: number; // -1, 0, or 1 (DELETE, EQUAL, INSERT)
    1: string; // text
  }

  export class DiffMatchPatch {
    diff_main(text1: string, text2: string, opt_checklines?: boolean): Diff[];
    diff_cleanupSemantic(diffs: Diff[]): void;
    diff_cleanupEfficiency(diffs: Diff[]): void;
  }
}