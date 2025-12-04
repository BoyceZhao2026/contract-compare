declare module 'mammoth' {
  export interface MammothOptions {
    arrayBuffer?: ArrayBuffer;
    path?: string;
    convertImage?: MammothImageConverter;
    ignoreEmptyParagraphs?: boolean;
    includeDefaultStyleMap?: boolean;
    styleMap?: string;
    transforms?: Array<(element: any) => any>;
  }

  export interface MammothImageConverter {
    (image: any): Promise<{ src: string }>;
  }

  export interface MammothResult {
    value: string;
    messages: Array<{ type: string; message: string }>;
  }

  export function extractRawText(options: MammothOptions): Promise<MammothResult>;
  export function convertToHtml(options: MammothOptions): Promise<MammothResult>;
}